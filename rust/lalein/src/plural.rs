//! CLDR plural rules for every language in the official `cldr-plurals.json`.
//! A faithful port of the Java `PluralResolvers` — same table encoding, same
//! rule semantics, verified against the same golden corpus.

/// Tolerance band around integer values — absorbs accumulated floating-point
/// error so that values like `99.9999999` are treated as `100`.
pub const EPSILON: f64 = 0.000001000001;

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum PluralType {
    Zero,
    One,
    Two,
    Few,
    Many,
    Other,
}

/// Language→rule table. Rule codes are single chars (anything but `/` or
/// a-z), language codes are lowercase letters separated by `/`.
const TABLE: &str = concat!(
    "A/ak/bho/guw/ln/mg/nso/pa/ti/wa/csw/",
    "B/am/as/bn/gu/hi/kn/pcm/fa/zu/doi/kok/",
    "C/ff/hy/kab/",
    "D/da/",
    "E/fr/pt/",
    "F/es/it/ca/lld/scn/vec/",
    "G/ru/uk/be/",
    "[/sr/hr/bs/sh/",
    "\\/lag/",
    "]/si/",
    "H/pl/",
    "I/cs/sk/",
    "J/lt/",
    "K/lv/prg/",
    "L/sl/",
    "^/dsb/hsb/",
    "M/ar/ars/",
    "N/he/",
    "O/cy/",
    "P/ga/",
    "Q/gd/",
    "R/gv/",
    "S/ro/mo/",
    "T/mt/",
    "U/is/mk/",
    "V/fil/ceb/tl/",
    "W/shi/",
    "X/tzm/",
    "Y/kw/",
    "Z/br/",
    "_/iu/naq/sat/se/sma/smi/smj/smn/sms/",
    "`/blo/cv/ksh/",
    "{/sgs/",
    "|/af/an/asa/ast/az/bal/bem/bez/bg/brx/ce/cgg/chr/ckb/de/dv/ee/el/en/eo",
    "/et/eu/fi/fo/fur/fy/gl/gsw/ha/haw/hu/ia/ie/io/jgo/jmc/ka/kaj/kcg/kk/kkj",
    "/kl/ks/ksb/ku/ky/lb/lg/lij/mas/mgo/ml/mn/mr/nah/nb/nd/ne/nl/nn/nnh/no/nr",
    "/ny/nyn/om/or/os/pap/ps/rm/rof/rwk/saq/sc/sd/sdh/seh/sn/so/sq/ss/ssy/st",
    "/sv/sw/syr/ta/te/teo/tig/tk/tn/tr/ts/ug/ur/uz/ve/vo/vun/wae/xh/xog/yi/",
);

/// Bitmask of rules whose CLDR definition is integer-only: any fractional
/// input on these rules immediately returns OTHER. Bits set for rules
/// 6, 7, 12, 14..16, 19, 23..25.
const INT_ONLY_RULES: u32 = (1 << 6)
    | (1 << 7)
    | (1 << 12)
    | (1 << 14)
    | (1 << 15)
    | (1 << 16)
    | (1 << 19)
    | (1 << 23)
    | (1 << 24)
    | (1 << 25);

/// A plural rule bound to a language. `PluralResolver::NONE` always returns
/// `None`, which the engine treats as `Other`.
#[derive(Clone, Copy, Debug)]
pub struct PluralResolver {
    rule: i32,
}

impl PluralResolver {
    pub const NONE: PluralResolver = PluralResolver { rule: -1 };

    /// Resolver for a bare language code ("en", "ru", "sgs", …).
    pub fn for_language(language: &str) -> PluralResolver {
        let mut buf = [0u8; 16];
        if language.len() + 2 > buf.len() {
            return Self::NONE;
        }
        buf[0] = b'/';
        buf[1..1 + language.len()].copy_from_slice(language.as_bytes());
        buf[1 + language.len()] = b'/';
        let needle = match core::str::from_utf8(&buf[..language.len() + 2]) {
            Ok(s) => s,
            Err(_) => return Self::NONE,
        };
        let bytes = TABLE.as_bytes();
        let mut idx = match TABLE.find(needle) {
            Some(i) => i,
            None => return Self::NONE,
        };
        let mut c = bytes[idx];
        while c == b'/' || c.is_ascii_lowercase() {
            idx -= 1;
            c = bytes[idx];
        }
        PluralResolver { rule: (c - b'A') as i32 }
    }

    /// Resolver for a language + region pair. CLDR gives European Portuguese
    /// its own rule — identical to the es/it/ca family (rule 5).
    pub fn for_locale(language: &str, region: &str) -> PluralResolver {
        if language == "pt" && region == "PT" {
            return PluralResolver { rule: 5 };
        }
        Self::for_language(language)
    }

    /// Resolver derived from the `LANG`/`LC_ALL` environment variables
    /// (e.g. "pt_PT.UTF-8"). Falls back to "en".
    pub fn for_current_locale() -> PluralResolver {
        for var in ["LC_ALL", "LANG"] {
            if let Ok(value) = std::env::var(var) {
                let bytes = value.as_bytes();
                let end = bytes
                    .iter()
                    .position(|&b| b == b'.' || b == b'@')
                    .unwrap_or(bytes.len());
                let code = &value[..end];
                let (lang, region) = match bytes[..end].iter().position(|&b| b == b'_') {
                    Some(u) => (&code[..u], &code[u + 1..]),
                    None => (code, ""),
                };
                if !lang.is_empty() {
                    return Self::for_locale(lang, region);
                }
            }
        }
        Self::for_language("en")
    }

    /// The CLDR category for a value, or `None` when the rule says "other".
    pub fn find_type(&self, num: f64) -> Option<PluralType> {
        if self.rule < 0 {
            return None;
        }
        resolve(self.rule, num)
    }
}

use PluralType::*;

fn resolve(rule: i32, num: f64) -> Option<PluralType> {
    let d = num;
    if d.is_nan() || d.is_infinite() {
        return None;
    }
    let abs_d = if d < 0.0 { -d } else { d };
    let rounded = d.round() as i64;
    let is_int = (d - rounded as f64).abs() <= EPSILON;
    let n: i64; // CLDR operand i (integer part, absolute)
    let mut v: i32 = 0; // CLDR operand v (visible fraction digits)
    let mut f: i64 = 0; // CLDR operand f (visible fraction digits as integer)
    if is_int {
        n = if rounded < 0 { -rounded } else { rounded };
    } else {
        if rule < 32 && (INT_ONLY_RULES >> rule) & 1 != 0 {
            return None;
        }
        n = abs_d as i64;
        // Extract the visible fraction digits (CLDR operands v and f)
        // arithmetically — avoids pulling float-to-string machinery into
        // the binary, which would cost tens of KB. The loop only runs for
        // non-integers, so the remainder always starts above EPSILON, and
        // the digits extracted match the shortest-repr digits (validated
        // against the 62k-row golden corpus).
        let mut rem = abs_d - n as f64;
        while v < 15 && rem > EPSILON {
            rem *= 10.0;
            let mut digit = rem as i64;
            rem -= digit as f64;
            if rem > 1.0 - EPSILON {
                digit += 1;
                rem = 0.0;
            }
            f = f * 10 + digit;
            v += 1;
        }
    }
    let m10 = n % 10;
    let m100 = n % 100;
    let fm10 = f % 10;
    let fm100 = f % 100;
    // "Visible" mod-10/100: i-digits for integers, f-digits for decimals.
    let umod10 = if v == 0 { m10 } else { fm10 };
    let umod100 = if v == 0 { m100 } else { fm100 };
    let million_many = is_int && n > 0 && n % 1_000_000 == 0;
    match rule {
        0 => {
            if is_int && (n == 0 || n == 1) { Some(One) } else { None }
        }
        1 => {
            if abs_d <= 1.0 + EPSILON { Some(One) } else { None }
        }
        2 => {
            if abs_d < 2.0 - EPSILON { Some(One) } else { None }
        }
        3 => {
            if abs_d > EPSILON && abs_d < 2.0 - EPSILON { Some(One) } else { None }
        }
        4 => {
            if million_many { Some(Many) } else if abs_d < 2.0 - EPSILON { Some(One) } else { None }
        }
        5 => {
            if million_many { Some(Many) } else if is_int && n == 1 { Some(One) } else { None }
        }
        6 => {
            // russian, ukrainian, belarusian
            if m10 == 1 && m100 != 11 { return Some(One); }
            if (2..=4).contains(&m10) && !(12..=14).contains(&m100) { return Some(Few); }
            Some(Many)
        }
        26 => {
            // serbian, croatian, bosnian, serbo-croatian (no "many"; decimal branches)
            if umod10 == 1 && umod100 != 11 { return Some(One); }
            if (2..=4).contains(&umod10) && !(12..=14).contains(&umod100) { Some(Few) } else { None }
        }
        7 => {
            // polish
            if n == 1 { return Some(One); }
            if (2..=4).contains(&m10) && !(12..=14).contains(&m100) { return Some(Few); }
            Some(Many)
        }
        8 => {
            // czech, slovak
            if v != 0 { return Some(Many); }
            if n == 1 { return Some(One); }
            if (2..=4).contains(&n) { Some(Few) } else { None }
        }
        9 => {
            // lithuanian
            if f != 0 { return Some(Many); }
            if (11..=19).contains(&m100) { return None; }
            if m10 == 1 { return Some(One); }
            if m10 >= 2 { Some(Few) } else { None }
        }
        10 => {
            // latvian, prussian
            if is_int && (m10 == 0 || (11..=19).contains(&m100)) { return Some(Zero); }
            if v == 2 && (11..=19).contains(&fm100) { return Some(Zero); }
            if is_int && m10 == 1 && m100 != 11 { return Some(One); }
            if v == 2 && fm10 == 1 && fm100 != 11 { return Some(One); }
            if v != 0 && v != 2 && fm10 == 1 { Some(One) } else { None }
        }
        11 => {
            // slovenian (all decimals → few per CLDR)
            if v != 0 { return Some(Few); }
            if m100 == 1 { return Some(One); }
            if m100 == 2 { return Some(Two); }
            if m100 == 3 || m100 == 4 { Some(Few) } else { None }
        }
        12 => {
            // arabic, najdi arabic
            if n == 0 { return Some(Zero); }
            if n == 1 { return Some(One); }
            if n == 2 { return Some(Two); }
            if (3..=10).contains(&m100) { return Some(Few); }
            if (11..=99).contains(&m100) { Some(Many) } else { None }
        }
        13 => {
            // hebrew (CLDR v42+: only one/two/other)
            if !is_int { return if n == 0 { Some(One) } else { None }; }
            if n == 1 { return Some(One); }
            if n == 2 { Some(Two) } else { None }
        }
        14 => {
            // welsh
            if n == 0 { return Some(Zero); }
            if n == 1 { return Some(One); }
            if n == 2 { return Some(Two); }
            if n == 3 { return Some(Few); }
            if n == 6 { Some(Many) } else { None }
        }
        15 => {
            // irish
            if n == 1 { return Some(One); }
            if n == 2 { return Some(Two); }
            if (3..=6).contains(&n) { return Some(Few); }
            if (7..=10).contains(&n) { Some(Many) } else { None }
        }
        16 => {
            // scottish gaelic
            if n == 1 || n == 11 { return Some(One); }
            if n == 2 || n == 12 { return Some(Two); }
            if (3..=10).contains(&n) || (13..=19).contains(&n) { Some(Few) } else { None }
        }
        17 => {
            // manx
            if v != 0 { return Some(Many); }
            if m10 == 1 { return Some(One); }
            if m10 == 2 { return Some(Two); }
            if m100 % 20 == 0 { Some(Few) } else { None }
        }
        18 => {
            // romanian, moldovan
            if v != 0 { return Some(Few); }
            if n == 1 { return Some(One); }
            if n == 0 || (1..=19).contains(&m100) { Some(Few) } else { None }
        }
        19 => {
            // maltese
            if n == 1 { return Some(One); }
            if n == 2 { return Some(Two); }
            if n == 0 || (3..=10).contains(&m100) { return Some(Few); }
            if (11..=19).contains(&m100) { Some(Many) } else { None }
        }
        20 => {
            // icelandic, macedonian (t/f decimal extension)
            if umod10 == 1 && umod100 != 11 { Some(One) } else { None }
        }
        21 => {
            // filipino, cebuano, tagalog
            if umod10 != 4 && umod10 != 6 && umod10 != 9 { Some(One) } else { None }
        }
        22 => {
            // tachelhit
            if n == 0 || (is_int && n == 1) { return Some(One); }
            if !is_int { return None; }
            if (2..=10).contains(&n) { Some(Few) } else { None }
        }
        23 => {
            // central atlas tamazight
            if n == 0 || n == 1 || (11..=99).contains(&n) { Some(One) } else { None }
        }
        24 => {
            // cornish
            if n == 0 { return Some(Zero); }
            if n == 1 { return Some(One); }
            let mod20 = m100 % 20;
            if mod20 == 3 { return Some(Few); }
            if mod20 == 1 { return Some(Many); }
            if mod20 == 2 { return Some(Two); }
            let m100k = n % 100000;
            if n % 1000 == 0
                && ((1000..=20000).contains(&m100k)
                    || m100k == 40000
                    || m100k == 60000
                    || m100k == 80000)
            {
                return Some(Two);
            }
            if n != 0 && n % 1_000_000 == 100000 { Some(Two) } else { None }
        }
        25 => {
            // breton — exclude tens digit 1/7/9 (11/71/91, 12/72/92, 13..19/73..79/93..99)
            let tens = m100 / 10;
            if tens != 1 && tens != 7 && tens != 9 {
                if m10 == 1 { return Some(One); }
                if m10 == 2 { return Some(Two); }
                if m10 == 3 || m10 == 4 || m10 == 9 { return Some(Few); }
            }
            if n != 0 && n % 1_000_000 == 0 { Some(Many) } else { None }
        }
        27 => {
            // lag (rule D + Zero category for n=0)
            if abs_d <= EPSILON { return Some(Zero); }
            if abs_d < 2.0 - EPSILON { Some(One) } else { None }
        }
        28 => {
            // sinhala — n = 0,1 or i = 0 and f = 1
            if is_int && (n == 0 || n == 1) { return Some(One); }
            if n == 0 && f == 1 { Some(One) } else { None }
        }
        29 => {
            // lower sorbian, upper sorbian (i or f mod 100 = 1/2/3..4)
            if umod100 == 1 { return Some(One); }
            if umod100 == 2 { return Some(Two); }
            if umod100 == 3 || umod100 == 4 { Some(Few) } else { None }
        }
        30 => {
            // inuktitut, nama, santali, sami family: one = 1, two = 2
            if is_int && n == 1 { return Some(One); }
            if is_int && n == 2 { Some(Two) } else { None }
        }
        31 => {
            // anii, chuvash, colognian: zero = 0, one = 1
            if is_int && n == 0 { return Some(Zero); }
            if is_int && n == 1 { Some(One) } else { None }
        }
        58 => {
            // samogitian (rule code '{'): decimals → many; two = 2
            if f != 0 { return Some(Many); }
            if m10 == 1 && m100 != 11 { return Some(One); }
            if n == 2 { return Some(Two); }
            if (2..=9).contains(&m10) && !(11..=19).contains(&m100) { Some(Few) } else { None }
        }
        59 => {
            // english-like family (rule code '|'): one = 1, everything else → other
            if is_int && n == 1 { Some(One) } else { None }
        }
        _ => None,
    }
}
