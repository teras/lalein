package lalein

import (
	"math"
	"strings"
	"testing"
)

func laleinWith(p *Parameter, lang string) *Lalein {
	l := New(map[string]*Translation{
		"n": {Format: "%{p}", Parameters: map[string]*Parameter{"p": p}},
	})
	l.SetPluralResolver(UsingLanguage(lang))
	return l
}

func format(t *testing.T, l *Lalein, handler string, args ...any) string {
	t.Helper()
	out, err := l.Format(handler, args...)
	if err != nil {
		t.Fatalf("Format(%q) error: %v", handler, err)
	}
	return out
}

func TestEnglishLike(t *testing.T) {
	l := laleinWith(&Parameter{ArgumentIndex: 1, Zero: S("ZERO"), One: S("ONE"), Other: "OTHER"}, "en")
	cases := map[any]string{0: "ZERO", 1: "ONE", 2: "OTHER", 5: "OTHER", 0.5: "OTHER"}
	for arg, want := range cases {
		if got := format(t, l, "n", arg); got != want {
			t.Errorf("format(%v) = %q, want %q", arg, got, want)
		}
	}
}

func TestNegativeValuesUseAbsoluteCategory(t *testing.T) {
	l := laleinWith(&Parameter{ArgumentIndex: 1, Zero: S("ZERO"), One: S("ONE"), Two: S("TWO"), Other: "OTHER"}, "en")
	cases := map[any]string{-1: "ONE", -2: "TWO", -5: "OTHER", -1.5: "OTHER"}
	for arg, want := range cases {
		if got := format(t, l, "n", arg); got != want {
			t.Errorf("format(%v) = %q, want %q", arg, got, want)
		}
	}
}

func TestZeroMapsToOnePunjabi(t *testing.T) {
	l := laleinWith(&Parameter{ArgumentIndex: 1, One: S("ONE"), Other: "OTHER"}, "pa")
	if got := format(t, l, "n", 0); got != "ONE" {
		t.Errorf("format(0) = %q, want ONE", got)
	}
	if got := format(t, l, "n", 3); got != "OTHER" {
		t.Errorf("format(3) = %q, want OTHER", got)
	}
}

func TestRussianFewMany(t *testing.T) {
	r := UsingLanguage("ru")
	cases := []struct {
		v    float64
		want PluralType
	}{{1, PluralOne}, {21, PluralOne}, {22, PluralFew}, {11, PluralMany}, {-21, PluralOne}}
	for _, c := range cases {
		if got, _ := r.FindType(c.v); got != c.want {
			t.Errorf("ru FindType(%v) = %v, want %v", c.v, got, c.want)
		}
	}
}

func TestPtPTDiffersFromPt(t *testing.T) {
	br, eu := UsingLanguage("pt"), UsingLocale("pt", "PT")
	if got, ok := br.FindType(0); !ok || got != PluralOne {
		t.Errorf("pt FindType(0) = %v,%v, want one,true", got, ok)
	}
	if _, ok := eu.FindType(0); ok {
		t.Errorf("pt-PT FindType(0) should be other")
	}
	for _, r := range []PluralResolver{br, eu} {
		if got, ok := r.FindType(1_000_000); !ok || got != PluralMany {
			t.Errorf("FindType(1e6) = %v,%v, want many,true", got, ok)
		}
	}
}

func TestNaNAndInfinityFallToOther(t *testing.T) {
	l := laleinWith(&Parameter{ArgumentIndex: 1, Zero: S("ZERO"), One: S("ONE"), Other: "OTHER"}, "en")
	for _, arg := range []any{math.NaN(), math.Inf(1), math.Inf(-1)} {
		if got := format(t, l, "n", arg); got != "OTHER" {
			t.Errorf("format(%v) = %q, want OTHER", arg, got)
		}
	}
}

func TestSelectModeGender(t *testing.T) {
	l := laleinWith(&Parameter{
		ArgumentIndex: 1,
		Other:         "They liked your post",
		Custom: map[string]string{
			"female": "She liked your post",
			"male":   "He liked your post",
		},
	}, "en")
	if got := format(t, l, "n", "female"); got != "She liked your post" {
		t.Errorf("got %q", got)
	}
	if got := format(t, l, "n", "other"); got != "They liked your post" {
		t.Errorf("got %q", got)
	}
	if got := format(t, l, "n", nil); got != "They liked your post" {
		t.Errorf("got %q", got)
	}
}

func TestNestedGenderAndCount(t *testing.T) {
	count := func(z, o, r string) *Parameter {
		return &Parameter{ArgumentIndex: 2, Zero: S(z), One: S(o), Other: r}
	}
	l := New(map[string]*Translation{
		"user_apples": {
			Format: "%{verb}",
			Parameters: map[string]*Parameter{
				"verb": {
					ArgumentIndex: 1,
					Other:         "%{other_count}",
					Custom: map[string]string{
						"female": "%{female_count}",
						"male":   "%{male_count}",
					},
				},
				"female_count": count("She doesn't have apples", "She has 1 apple", "She has %2$d apples"),
				"male_count":   count("He doesn't have apples", "He has 1 apple", "He has %2$d apples"),
				"other_count":  count("They don't have apples", "They have 1 apple", "They have %2$d apples"),
			},
		},
	})
	l.SetPluralResolver(UsingLanguage("en"))
	cases := []struct {
		args []any
		want string
	}{
		{[]any{"female", 0}, "She doesn't have apples"},
		{[]any{"female", 5}, "She has 5 apples"},
		{[]any{"male", 1}, "He has 1 apple"},
		{[]any{"other", 0}, "They don't have apples"},
	}
	for _, c := range cases {
		if got := format(t, l, "user_apples", c.args...); got != c.want {
			t.Errorf("format(%v) = %q, want %q", c.args, got, c.want)
		}
	}
}

func TestCounterIsNotFirstArgument(t *testing.T) {
	l := New(map[string]*Translation{
		"payment": {
			Format: "%{main}",
			Parameters: map[string]*Parameter{
				"main": {
					ArgumentIndex: 2,
					One:           S("Cash payment of %1$s saved (1 month allocated)."),
					Other:         "Cash payment of %1$s saved (%2$d months allocated).",
				},
			},
		},
	})
	l.SetPluralResolver(UsingLanguage("en"))
	if got := format(t, l, "payment", "12.34", 1); got != "Cash payment of 12.34 saved (1 month allocated)." {
		t.Errorf("got %q", got)
	}
	if got := format(t, l, "payment", "12.34", 7); got != "Cash payment of 12.34 saved (7 months allocated)." {
		t.Errorf("got %q", got)
	}
}

func TestUnknownHandlerIsUsedAsTemplate(t *testing.T) {
	l := New(nil)
	if got := format(t, l, "I have %d apples", 5); got != "I have 5 apples" {
		t.Errorf("got %q", got)
	}
	if got := format(t, l, "100%%"); got != "100%" {
		t.Errorf("got %q", got)
	}
}

func TestPrintfPositionalAndTypes(t *testing.T) {
	l := New(nil)
	if got := format(t, l, "%2$d of %1$d items", 3, 5); got != "5 of 3 items" {
		t.Errorf("got %q", got)
	}
	if got := format(t, l, "%s has %d (%f-ish, %x)", "x", 7, 2.5, 255); got != "x has 7 (2.500000-ish, ff)" {
		t.Errorf("got %q", got)
	}
	if got := format(t, l, "%s", 5.0); got != "5.0" {
		t.Errorf("got %q", got)
	}
}

func TestMissingParameterIsAnError(t *testing.T) {
	l := New(map[string]*Translation{
		"n": {Format: "%{missing}", Parameters: map[string]*Parameter{
			"p": {ArgumentIndex: 1, Other: "OTHER"},
		}},
	})
	_, err := l.Format("n", 1)
	if err == nil || !strings.Contains(err.Error(), "unable to locate localization parameter 'missing' in 'n'") {
		t.Errorf("expected missing-parameter error, got %v", err)
	}
}
