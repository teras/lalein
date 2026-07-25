package lalein

// CLDR plural rules for every language in the official cldr-plurals.json.
// A faithful port of the Java PluralResolvers — same table encoding, same
// rule semantics, verified against the same golden corpus.

import (
	"math"
	"os"
	"strings"
)

// epsilon is the tolerance band around integer values — absorbs accumulated
// floating-point error so that values like 99.9999999 are treated as 100.
const epsilon = 0.000001000001

// PluralType is a CLDR plural category.
type PluralType int

const (
	PluralZero PluralType = iota
	PluralOne
	PluralTwo
	PluralFew
	PluralMany
	PluralOther
)

func (p PluralType) String() string {
	switch p {
	case PluralZero:
		return "zero"
	case PluralOne:
		return "one"
	case PluralTwo:
		return "two"
	case PluralFew:
		return "few"
	case PluralMany:
		return "many"
	}
	return "other"
}

// Language→rule table. Rule codes are single chars (anything but '/' or
// a-z), language codes are lowercase letters separated by '/'.
const table = "" +
	"A/ak/bho/guw/ln/mg/nso/pa/ti/wa/csw/" +
	"B/am/as/bn/gu/hi/kn/pcm/fa/zu/doi/kok/" +
	"C/ff/hy/kab/" +
	"D/da/" +
	"E/fr/pt/" +
	"F/es/it/ca/lld/scn/vec/" +
	"G/ru/uk/be/" +
	"[/sr/hr/bs/sh/" +
	"\\/lag/" +
	"]/si/" +
	"H/pl/" +
	"I/cs/sk/" +
	"J/lt/" +
	"K/lv/prg/" +
	"L/sl/" +
	"^/dsb/hsb/" +
	"M/ar/ars/" +
	"N/he/" +
	"O/cy/" +
	"P/ga/" +
	"Q/gd/" +
	"R/gv/" +
	"S/ro/mo/" +
	"T/mt/" +
	"U/is/mk/" +
	"V/fil/ceb/tl/" +
	"W/shi/" +
	"X/tzm/" +
	"Y/kw/" +
	"Z/br/" +
	"_/iu/naq/sat/se/sma/smi/smj/smn/sms/" +
	"`/blo/cv/ksh/" +
	"{/sgs/" +
	"|/af/an/asa/ast/az/bal/bem/bez/bg/brx/ce/cgg/chr/ckb/de/dv/ee/el/en/eo" +
	"/et/eu/fi/fo/fur/fy/gl/gsw/ha/haw/hu/ia/ie/io/jgo/jmc/ka/kaj/kcg/kk/kkj" +
	"/kl/ks/ksb/ku/ky/lb/lg/lij/mas/mgo/ml/mn/mr/nah/nb/nd/ne/nl/nn/nnh/no/nr" +
	"/ny/nyn/om/or/os/pap/ps/rm/rof/rwk/saq/sc/sd/sdh/seh/sn/so/sq/ss/ssy/st" +
	"/sv/sw/syr/ta/te/teo/tig/tk/tn/tr/ts/ug/ur/uz/ve/vo/vun/wae/xh/xog/yi/"

// Bitmask of rules whose CLDR definition is integer-only: any fractional
// input on these rules immediately returns other. Bits set for rules
// 6, 7, 12, 14..16, 19, 23..25.
const intOnlyRules = (1 << 6) | (1 << 7) | (1 << 12) | (1 << 14) | (1 << 15) |
	(1 << 16) | (1 << 19) | (1 << 23) | (1 << 24) | (1 << 25)

// PluralResolver is a plural rule bound to a language. The zero value and
// NoResolver never match a category, which the engine treats as "other".
type PluralResolver struct {
	rule int
}

// NoResolver always reports "other".
var NoResolver = PluralResolver{-1}

// UsingLanguage returns the resolver for a bare language code ("en", "ru", …).
func UsingLanguage(language string) PluralResolver {
	idx := strings.Index(table, "/"+language+"/")
	if idx < 0 {
		return NoResolver
	}
	c := table[idx]
	for c == '/' || (c >= 'a' && c <= 'z') {
		idx--
		c = table[idx]
	}
	return PluralResolver{int(c) - int('A')}
}

// UsingLocale returns the resolver for a language + region pair. CLDR gives
// European Portuguese its own rule — identical to the es/it/ca family (rule 5).
func UsingLocale(language, region string) PluralResolver {
	if language == "pt" && region == "PT" {
		return PluralResolver{5}
	}
	return UsingLanguage(language)
}

// UsingCurrentLocale derives the resolver from the LC_ALL/LANG environment
// variables (e.g. "pt_PT.UTF-8"). Falls back to "en".
func UsingCurrentLocale() PluralResolver {
	for _, env := range []string{"LC_ALL", "LANG"} {
		value := os.Getenv(env)
		if value == "" {
			continue
		}
		code := value
		if i := strings.IndexByte(code, '.'); i >= 0 {
			code = code[:i]
		}
		if i := strings.IndexByte(code, '@'); i >= 0 {
			code = code[:i]
		}
		lang, region, _ := strings.Cut(code, "_")
		if lang != "" {
			return UsingLocale(lang, region)
		}
	}
	return UsingLanguage("en")
}

// FindType returns the CLDR category for a value; ok is false when the rule
// has no specific category, meaning "other".
func (r PluralResolver) FindType(num float64) (category PluralType, ok bool) {
	if r.rule < 0 {
		return PluralOther, false
	}
	return resolve(r.rule, num)
}

func resolve(rule int, num float64) (PluralType, bool) {
	d := num
	if math.IsNaN(d) || math.IsInf(d, 0) {
		return PluralOther, false
	}
	absD := math.Abs(d)
	rounded := int64(math.Floor(d + 0.5)) // Java Math.round semantics
	isInt := math.Abs(d-float64(rounded)) <= epsilon
	var n int64  // CLDR operand i (integer part, absolute)
	var v int    // CLDR operand v (visible fraction digits)
	var f int64  // CLDR operand f (visible fraction digits as integer)
	if isInt {
		if rounded < 0 {
			n = -rounded
		} else {
			n = rounded
		}
	} else {
		if rule < 32 && (intOnlyRules>>rule)&1 != 0 {
			return PluralOther, false
		}
		n = int64(absD)
		// Extract the visible fraction digits (CLDR operands v and f)
		// arithmetically — validated against the 62k-row golden corpus.
		rem := absD - float64(n)
		for v < 15 && rem > epsilon {
			rem *= 10
			digit := int64(rem)
			rem -= float64(digit)
			if rem > 1.0-epsilon {
				digit++
				rem = 0
			}
			f = f*10 + digit
			v++
		}
	}
	m10, m100 := n%10, n%100
	fm10, fm100 := f%10, f%100
	// "Visible" mod-10/100: i-digits for integers, f-digits for decimals.
	umod10, umod100 := m10, m100
	if v != 0 {
		umod10, umod100 = fm10, fm100
	}
	millionMany := isInt && n > 0 && n%1000000 == 0
	switch rule {
	case 0:
		return PluralOne, isInt && (n == 0 || n == 1)
	case 1:
		return PluralOne, absD <= 1+epsilon
	case 2:
		return PluralOne, absD < 2-epsilon
	case 3:
		return PluralOne, absD > epsilon && absD < 2-epsilon
	case 4:
		if millionMany {
			return PluralMany, true
		}
		return PluralOne, absD < 2-epsilon
	case 5:
		if millionMany {
			return PluralMany, true
		}
		return PluralOne, isInt && n == 1
	case 6: // russian, ukrainian, belarusian
		if m10 == 1 && m100 != 11 {
			return PluralOne, true
		}
		if m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14) {
			return PluralFew, true
		}
		return PluralMany, true
	case 26: // serbian, croatian, bosnian, serbo-croatian (no "many"; decimal branches)
		if umod10 == 1 && umod100 != 11 {
			return PluralOne, true
		}
		return PluralFew, umod10 >= 2 && umod10 <= 4 && (umod100 < 12 || umod100 > 14)
	case 7: // polish
		if n == 1 {
			return PluralOne, true
		}
		if m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14) {
			return PluralFew, true
		}
		return PluralMany, true
	case 8: // czech, slovak
		if v != 0 {
			return PluralMany, true
		}
		if n == 1 {
			return PluralOne, true
		}
		return PluralFew, n >= 2 && n <= 4
	case 9: // lithuanian
		if f != 0 {
			return PluralMany, true
		}
		if m100 >= 11 && m100 <= 19 {
			return PluralOther, false
		}
		if m10 == 1 {
			return PluralOne, true
		}
		return PluralFew, m10 >= 2
	case 10: // latvian, prussian
		if isInt && (m10 == 0 || (m100 >= 11 && m100 <= 19)) {
			return PluralZero, true
		}
		if v == 2 && fm100 >= 11 && fm100 <= 19 {
			return PluralZero, true
		}
		if isInt && m10 == 1 && m100 != 11 {
			return PluralOne, true
		}
		if v == 2 && fm10 == 1 && fm100 != 11 {
			return PluralOne, true
		}
		return PluralOne, v != 0 && v != 2 && fm10 == 1
	case 11: // slovenian (all decimals → few per CLDR)
		if v != 0 {
			return PluralFew, true
		}
		if m100 == 1 {
			return PluralOne, true
		}
		if m100 == 2 {
			return PluralTwo, true
		}
		return PluralFew, m100 == 3 || m100 == 4
	case 12: // arabic, najdi arabic
		if n == 0 {
			return PluralZero, true
		}
		if n == 1 {
			return PluralOne, true
		}
		if n == 2 {
			return PluralTwo, true
		}
		if m100 >= 3 && m100 <= 10 {
			return PluralFew, true
		}
		return PluralMany, m100 >= 11 && m100 <= 99
	case 13: // hebrew (CLDR v42+: only one/two/other)
		if !isInt {
			return PluralOne, n == 0
		}
		if n == 1 {
			return PluralOne, true
		}
		return PluralTwo, n == 2
	case 14: // welsh
		if n == 0 {
			return PluralZero, true
		}
		if n == 1 {
			return PluralOne, true
		}
		if n == 2 {
			return PluralTwo, true
		}
		if n == 3 {
			return PluralFew, true
		}
		return PluralMany, n == 6
	case 15: // irish
		if n == 1 {
			return PluralOne, true
		}
		if n == 2 {
			return PluralTwo, true
		}
		if n >= 3 && n <= 6 {
			return PluralFew, true
		}
		return PluralMany, n >= 7 && n <= 10
	case 16: // scottish gaelic
		if n == 1 || n == 11 {
			return PluralOne, true
		}
		if n == 2 || n == 12 {
			return PluralTwo, true
		}
		return PluralFew, (n >= 3 && n <= 10) || (n >= 13 && n <= 19)
	case 17: // manx
		if v != 0 {
			return PluralMany, true
		}
		if m10 == 1 {
			return PluralOne, true
		}
		if m10 == 2 {
			return PluralTwo, true
		}
		return PluralFew, m100%20 == 0
	case 18: // romanian, moldovan
		if v != 0 {
			return PluralFew, true
		}
		if n == 1 {
			return PluralOne, true
		}
		return PluralFew, n == 0 || (m100 >= 1 && m100 <= 19)
	case 19: // maltese
		if n == 1 {
			return PluralOne, true
		}
		if n == 2 {
			return PluralTwo, true
		}
		if n == 0 || (m100 >= 3 && m100 <= 10) {
			return PluralFew, true
		}
		return PluralMany, m100 >= 11 && m100 <= 19
	case 20: // icelandic, macedonian (t/f decimal extension)
		return PluralOne, umod10 == 1 && umod100 != 11
	case 21: // filipino, cebuano, tagalog
		return PluralOne, umod10 != 4 && umod10 != 6 && umod10 != 9
	case 22: // tachelhit
		if n == 0 || (isInt && n == 1) {
			return PluralOne, true
		}
		if !isInt {
			return PluralOther, false
		}
		return PluralFew, n >= 2 && n <= 10
	case 23: // central atlas tamazight
		return PluralOne, n == 0 || n == 1 || (n >= 11 && n <= 99)
	case 24: // cornish
		if n == 0 {
			return PluralZero, true
		}
		if n == 1 {
			return PluralOne, true
		}
		switch mod20 := m100 % 20; mod20 {
		case 3:
			return PluralFew, true
		case 1:
			return PluralMany, true
		case 2:
			return PluralTwo, true
		}
		m100k := n % 100000
		if n%1000 == 0 && ((m100k >= 1000 && m100k <= 20000) ||
			m100k == 40000 || m100k == 60000 || m100k == 80000) {
			return PluralTwo, true
		}
		return PluralTwo, n != 0 && n%1000000 == 100000
	case 25: // breton — exclude tens digit 1/7/9 (11/71/91, 12/72/92, 13..19/73..79/93..99)
		if tens := m100 / 10; tens != 1 && tens != 7 && tens != 9 {
			if m10 == 1 {
				return PluralOne, true
			}
			if m10 == 2 {
				return PluralTwo, true
			}
			if m10 == 3 || m10 == 4 || m10 == 9 {
				return PluralFew, true
			}
		}
		return PluralMany, n != 0 && n%1000000 == 0
	case 27: // lag (rule D + ZERO category for n=0)
		if absD <= epsilon {
			return PluralZero, true
		}
		return PluralOne, absD < 2-epsilon
	case 28: // sinhala — n = 0,1 or i = 0 and f = 1
		if isInt && (n == 0 || n == 1) {
			return PluralOne, true
		}
		return PluralOne, n == 0 && f == 1
	case 29: // lower sorbian, upper sorbian (i or f mod 100 = 1/2/3..4)
		if umod100 == 1 {
			return PluralOne, true
		}
		if umod100 == 2 {
			return PluralTwo, true
		}
		return PluralFew, umod100 == 3 || umod100 == 4
	case 30: // inuktitut, nama, santali, sami family: one = 1, two = 2
		if isInt && n == 1 {
			return PluralOne, true
		}
		return PluralTwo, isInt && n == 2
	case 31: // anii, chuvash, colognian: zero = 0, one = 1
		if isInt && n == 0 {
			return PluralZero, true
		}
		return PluralOne, isInt && n == 1
	case 58: // samogitian (rule code '{'): decimals → many; two = 2
		if f != 0 {
			return PluralMany, true
		}
		if m10 == 1 && m100 != 11 {
			return PluralOne, true
		}
		if n == 2 {
			return PluralTwo, true
		}
		return PluralFew, m10 >= 2 && m10 <= 9 && (m100 < 11 || m100 > 19)
	case 59: // english-like family (rule code '|'): one = 1, everything else → other
		return PluralOne, isInt && n == 1
	}
	return PluralOther, false
}
