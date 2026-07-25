package lalein

// The resolution engine: translations, parameters, %{name} references and
// the final printf pass. A faithful port of the Java Lalein, Translation
// and Parameter classes.

import (
	"errors"
	"strconv"
)

// Parameter is a plural- or select-mode parameter of a translation unit.
//
// The CLDR plural keys are the Zero..Many fields plus the mandatory Other
// fallback. Any entry in Custom is a select-mode key (gender, formality, …)
// chosen by string arguments.
type Parameter struct {
	// ArgumentIndex is the 1-based index of the driving argument.
	ArgumentIndex int
	Zero          *string
	One           *string
	Two           *string
	Few           *string
	Many          *string
	Other         string
	Custom        map[string]string
}

// S returns a pointer to s, for concise plural form literals.
func S(s string) *string { return &s }

func (p *Parameter) resolve(resolver PluralResolver, handler, name string, args []any) (string, error) {
	where := " (parameter '" + name + "' of '" + handler + "')"
	if len(args) == 0 {
		return "", errors.New("a numeric argument is required but none was given" + where)
	}
	if len(args) < p.ArgumentIndex {
		return "", errors.New("a numeric argument at position #" + strconv.Itoa(p.ArgumentIndex) +
			" is required but only " + strconv.Itoa(len(args)) + " argument(s) given" + where)
	}
	arg := args[p.ArgumentIndex-1]
	if d, ok := asNumber(arg); ok {
		rounded := int64(floorHalfUp(d))
		if abs64(d-float64(rounded)) <= epsilon {
			// CLDR defines n = abs(source): -1 takes the "one" form, etc.
			absRounded := rounded
			if absRounded < 0 {
				absRounded = -absRounded
			}
			if absRounded == 0 && p.Zero != nil {
				return *p.Zero, nil
			}
			if absRounded == 1 && p.One != nil {
				return *p.One, nil
			}
			if absRounded == 2 && p.Two != nil {
				return *p.Two, nil
			}
		}
		category, ok := resolver.FindType(d)
		if !ok {
			category = PluralOther
		}
		var form *string
		switch category {
		case PluralZero:
			form = p.Zero
		case PluralOne:
			form = p.One
		case PluralTwo:
			form = p.Two
		case PluralFew:
			form = p.Few
		case PluralMany:
			form = p.Many
		}
		if form != nil {
			return *form, nil
		}
		return p.Other, nil
	}
	if len(p.Custom) == 0 {
		kind := "null"
		if _, ok := arg.(string); ok {
			kind = "string"
		}
		return "", errors.New("a numeric argument at position #" + strconv.Itoa(p.ArgumentIndex) +
			" is required but got " + kind + where)
	}
	if key, ok := arg.(string); ok {
		if form, found := p.Custom[key]; found {
			return form, nil
		}
	}
	return p.Other, nil
}

// Translation is a translation unit: a master template plus named parameters.
type Translation struct {
	Format     string
	Parameters map[string]*Parameter
}

// Lalein is the translation registry and formatting entry point.
type Lalein struct {
	registry      map[string]*Translation
	resolver      PluralResolver
	postProcessor func(string) string
}

// New creates a Lalein over the given registry, using the current locale's
// plural rules.
func New(registry map[string]*Translation) *Lalein {
	return &Lalein{registry: registry, resolver: UsingCurrentLocale()}
}

// SetPluralResolver overrides the plural rules used by Format.
func (l *Lalein) SetPluralResolver(resolver PluralResolver) {
	l.resolver = resolver
}

// SetPostProcessor installs a hook applied to the template right before the
// final printf pass.
func (l *Lalein) SetPostProcessor(processor func(string) string) {
	l.postProcessor = processor
}

// Format formats the translation unit handler with positional args. When the
// handler is unknown, the handler itself is used as the template.
func (l *Lalein) Format(handler string, args ...any) (string, error) {
	format := handler
	if translation, found := l.registry[handler]; found {
		resolved, err := l.resolve(handler, translation, args)
		if err != nil {
			return "", err
		}
		format = resolved
	}
	if l.postProcessor != nil {
		format = l.postProcessor(format)
	}
	return sprintf(format, args)
}

// resolve recursively replaces %{name} references with the resolved parameter
// values. Mirrors the Java implementation: after each replacement the scan
// restarts, so references produced by a parameter are resolved too.
func (l *Lalein) resolve(handler string, translation *Translation, args []any) (string, error) {
	format := translation.Format
	if len(translation.Parameters) == 0 {
		return format, nil
	}
	i := 0
	for i+2 < len(format) {
		if format[i] == '%' && format[i+1] == '{' {
			j := i + 2
			for j < len(format) && (isWordChar(format[j])) {
				j++
			}
			if j > i+2 && j < len(format) && format[j] == '}' {
				name := format[i+2 : j]
				parameter, found := translation.Parameters[name]
				if !found {
					return "", errors.New("unable to locate localization parameter '" + name + "' in '" + handler + "'")
				}
				value, err := parameter.resolve(l.resolver, handler, name, args)
				if err != nil {
					return "", err
				}
				format = format[:i] + value + format[j+1:]
				i = 0
				continue
			}
		}
		i++
	}
	return format, nil
}

func isWordChar(c byte) bool {
	return c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
}

func asNumber(v any) (float64, bool) {
	switch n := v.(type) {
	case int:
		return float64(n), true
	case int8:
		return float64(n), true
	case int16:
		return float64(n), true
	case int32:
		return float64(n), true
	case int64:
		return float64(n), true
	case uint:
		return float64(n), true
	case uint8:
		return float64(n), true
	case uint16:
		return float64(n), true
	case uint32:
		return float64(n), true
	case uint64:
		return float64(n), true
	case float32:
		return float64(n), true
	case float64:
		return n, true
	}
	return 0, false
}
