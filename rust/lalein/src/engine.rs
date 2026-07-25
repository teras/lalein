//! The resolution engine: translations, parameters, `%{name}` references and
//! the final printf pass. A faithful port of the Java `Lalein`, `Translation`
//! and `Parameter` classes.

use crate::plural::{PluralResolver, PluralType, EPSILON};
use crate::printf;
use std::error::Error;
use std::fmt;

#[derive(Debug)]
pub struct LaleinError(pub String);

impl fmt::Display for LaleinError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

impl Error for LaleinError {}

/// One positional argument of a [`Lalein::format`] call.
#[derive(Clone, Copy, Debug)]
pub enum Arg<'a> {
    Int(i64),
    Float(f64),
    Str(&'a str),
    None,
}

impl Arg<'_> {
    fn as_f64(self) -> Option<f64> {
        match self {
            Arg::Int(i) => Some(i as f64),
            Arg::Float(f) => Some(f),
            _ => None,
        }
    }
}

/// A plural- or select-mode parameter of a translation unit.
///
/// The CLDR plural keys are the `zero`..`many` fields plus the mandatory
/// `other` fallback. Any entry in `custom` is a select-mode key (gender,
/// formality, …) chosen by [`Arg::Str`] arguments.
#[derive(Clone, Debug, Default)]
pub struct Parameter {
    /// 1-based index of the driving argument.
    pub argument_index: usize,
    pub zero: Option<String>,
    pub one: Option<String>,
    pub two: Option<String>,
    pub few: Option<String>,
    pub many: Option<String>,
    pub other: String,
    pub custom: Vec<(String, String)>,
}

impl Parameter {
    fn resolve(
        &self,
        resolver: &PluralResolver,
        handler: &str,
        name: &str,
        args: &[Arg],
    ) -> Result<String, LaleinError> {
        let where_ = format!(" (parameter '{name}' of '{handler}')");
        if args.is_empty() {
            return Err(LaleinError(format!(
                "A numeric argument is required but none was given{where_}"
            )));
        }
        if args.len() < self.argument_index {
            return Err(LaleinError(format!(
                "A numeric argument at position #{} is required but only {} argument{} given{}",
                self.argument_index,
                args.len(),
                if args.len() == 1 { " was" } else { "s were" },
                where_
            )));
        }
        let arg = args[self.argument_index - 1];
        if let Some(d) = arg.as_f64() {
            let rounded = d.round() as i64;
            if (d - rounded as f64).abs() <= EPSILON {
                // CLDR defines n = abs(source): -1 takes the "one" form, etc.
                let abs = if rounded < 0 { -rounded } else { rounded };
                if abs == 0 && self.zero.is_some() {
                    return Ok(self.zero.clone().unwrap());
                }
                if abs == 1 && self.one.is_some() {
                    return Ok(self.one.clone().unwrap());
                }
                if abs == 2 && self.two.is_some() {
                    return Ok(self.two.clone().unwrap());
                }
            }
            let plural_type = resolver.find_type(d).unwrap_or(PluralType::Other);
            let form = match plural_type {
                PluralType::Zero => &self.zero,
                PluralType::One => &self.one,
                PluralType::Two => &self.two,
                PluralType::Few => &self.few,
                PluralType::Many => &self.many,
                PluralType::Other => &None,
            };
            return Ok(match form {
                Some(f) => f.clone(),
                None => self.other.clone(),
            });
        }
        if self.custom.is_empty() {
            return Err(LaleinError(format!(
                "A numeric argument at position #{} is required but got {}{}",
                self.argument_index,
                match arg {
                    Arg::Str(_) => "string",
                    Arg::None => "null",
                    _ => "unknown",
                },
                where_
            )));
        }
        let form = match arg {
            Arg::Str(key) => self
                .custom
                .iter()
                .find(|(k, _)| k == key)
                .map(|(_, v)| v.clone()),
            _ => None,
        };
        Ok(form.unwrap_or_else(|| self.other.clone()))
    }
}

/// A translation unit: a master template plus its named parameters.
#[derive(Clone, Debug)]
pub struct Translation {
    pub format: String,
    pub parameters: Vec<(String, Parameter)>,
}

impl Translation {
    pub fn simple(format: &str) -> Translation {
        Translation { format: format.to_string(), parameters: Vec::new() }
    }
}

/// The translation registry and formatting entry point.
pub struct Lalein {
    registry: Vec<(String, Translation)>,
    resolver: PluralResolver,
    post_processor: Option<Box<dyn Fn(String) -> String>>,
}

impl Lalein {
    pub fn new(registry: Vec<(String, Translation)>) -> Lalein {
        Lalein {
            registry,
            resolver: PluralResolver::for_current_locale(),
            post_processor: None,
        }
    }

    pub fn set_plural_resolver(&mut self, resolver: PluralResolver) {
        self.resolver = resolver;
    }

    pub fn set_post_processor(&mut self, processor: Option<Box<dyn Fn(String) -> String>>) {
        self.post_processor = processor;
    }

    /// Formats the translation unit `handler` with positional `args`. When
    /// the handler is unknown, the handler itself is used as the template.
    pub fn format(&self, handler: &str, args: &[Arg]) -> Result<String, LaleinError> {
        let translation = self
            .registry
            .iter()
            .find(|(h, _)| h == handler)
            .map(|(_, t)| t);
        let mut format = match translation {
            None => handler.to_string(),
            Some(t) => self.resolve(handler, t, args)?,
        };
        if let Some(pp) = &self.post_processor {
            format = pp(format);
        }
        printf::printf(&format, args)
    }

    /// Recursively replaces `%{name}` references with the resolved parameter
    /// values. Mirrors the Java implementation: after each replacement the
    /// scan restarts, so references produced by a parameter are resolved too.
    fn resolve(
        &self,
        handler: &str,
        translation: &Translation,
        args: &[Arg],
    ) -> Result<String, LaleinError> {
        let mut format = translation.format.clone();
        if translation.parameters.is_empty() {
            return Ok(format);
        }
        let mut i = 0;
        while i + 2 < format.len() {
            let bytes = format.as_bytes();
            if bytes[i] == b'%' && bytes[i + 1] == b'{' {
                let mut j = i + 2;
                while j < bytes.len() && (bytes[j].is_ascii_alphanumeric() || bytes[j] == b'_') {
                    j += 1;
                }
                if j > i + 2 && j < bytes.len() && bytes[j] == b'}' {
                    let name = &format[i + 2..j];
                    let parameter = translation
                        .parameters
                        .iter()
                        .find(|(n, _)| n == name)
                        .map(|(_, p)| p)
                        .ok_or_else(|| {
                            LaleinError(format!(
                                "Unable to locate localization parameter '{name}' in '{handler}'"
                            ))
                        })?;
                    let value = parameter.resolve(&self.resolver, handler, name, args)?;
                    format.replace_range(i..=j, &value);
                    i = 0;
                    continue;
                }
            }
            i += 1;
        }
        Ok(format)
    }
}
