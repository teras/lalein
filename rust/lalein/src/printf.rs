//! A minimal printf pass supporting the placeholders translation files
//! actually use: `%d`, `%s`, `%f`, `%x`/`%X`, `%%` and positional references
//! like `%2$d`. Width/precision/flags are not supported (yet) — the Java
//! version delegates to `String.format`, here we keep the footprint tiny.

use crate::engine::{Arg, LaleinError};

pub(crate) fn printf(fmt: &str, args: &[Arg]) -> Result<String, LaleinError> {
    let mut out = String::with_capacity(fmt.len() + 16);
    let mut sequential = 0usize;
    let bytes = fmt.as_bytes();
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] != b'%' {
            // Copy a run of plain text without scanning byte by byte.
            let start = i;
            while i < bytes.len() && bytes[i] != b'%' {
                i += 1;
            }
            out.push_str(&fmt[start..i]);
            continue;
        }
        i += 1;
        if i >= bytes.len() {
            return Err(LaleinError("dangling '%' at end of format string".into()));
        }
        if bytes[i] == b'%' {
            out.push('%');
            i += 1;
            continue;
        }
        // Optional positional reference: digits followed by '$'.
        let mut explicit = None;
        let mut j = i;
        while j < bytes.len() && bytes[j].is_ascii_digit() {
            j += 1;
        }
        if j > i && j < bytes.len() && bytes[j] == b'$' {
            explicit = Some(
                fmt[i..j]
                    .parse::<usize>()
                    .map_err(|_| LaleinError(format!("invalid positional index in '{fmt}'")))?,
            );
            i = j + 1;
        }
        if i >= bytes.len() {
            return Err(LaleinError("dangling '%' at end of format string".into()));
        }
        let index = match explicit {
            Some(pos) => {
                if pos == 0 {
                    return Err(LaleinError("argument index starts at 1".into()));
                }
                pos - 1
            }
            None => {
                let idx = sequential;
                sequential += 1;
                idx
            }
        };
        let arg = args.get(index).copied().ok_or_else(|| {
            LaleinError(format!(
                "format string references argument #{} but only {} given",
                index + 1,
                args.len()
            ))
        })?;
        match bytes[i] {
            b'd' | b'i' => match arg {
                Arg::Int(v) => out.push_str(&v.to_string()),
                Arg::Float(v) if v.fract() == 0.0 && v.abs() < 9.0e15 => {
                    out.push_str(&(v as i64).to_string())
                }
                _ => {
                    return Err(LaleinError(format!(
                        "%d requires an integral argument at position #{}",
                        index + 1
                    )))
                }
            },
            b's' => match arg {
                Arg::Int(v) => out.push_str(&v.to_string()),
                Arg::Float(v) => out.push_str(&double_to_string(v)),
                Arg::Str(s) => out.push_str(s),
                Arg::None => out.push_str("null"),
            },
            b'f' => match arg {
                Arg::Int(v) => out.push_str(&format!("{:.6}", v as f64)),
                Arg::Float(v) => out.push_str(&format!("{:.6}", v)),
                _ => {
                    return Err(LaleinError(format!(
                        "%f requires a numeric argument at position #{}",
                        index + 1
                    )))
                }
            },
            b'x' | b'X' => match arg {
                Arg::Int(v) if bytes[i] == b'x' => out.push_str(&format!("{:x}", v)),
                Arg::Int(v) => out.push_str(&format!("{:X}", v)),
                _ => {
                    return Err(LaleinError(format!(
                        "%x requires an integral argument at position #{}",
                        index + 1
                    )))
                }
            },
            c => {
                return Err(LaleinError(format!(
                    "unsupported conversion '%{}' — supported: %d %s %f %x %%",
                    c as char
                )))
            }
        }
        i += 1;
    }
    Ok(out)
}

/// Mimics Java's `Double.toString` for the `%s` conversion: integral values
/// keep a trailing ".0" ("5.0", not "5").
fn double_to_string(v: f64) -> String {
    if v.is_finite() && v.fract() == 0.0 && v.abs() < 1.0e15 {
        format!("{:.1}", v)
    } else {
        v.to_string()
    }
}
