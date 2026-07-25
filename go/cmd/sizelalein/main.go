package main

// Size harness. Build both cmds and compare:
//
//	go build -ldflags="-s -w" -o /tmp/go-baseline ./cmd/sizebaseline
//	go build -ldflags="-s -w" -o /tmp/go-with-lalein ./cmd/sizelalein
//
// The byte difference is exactly what linking lalein costs — runtime and
// fmt machinery are identical in both.

import (
	"fmt"

	"lalein"
)

func main() {
	count := func(z, o, r string) *lalein.Parameter {
		return &lalein.Parameter{ArgumentIndex: 2, Zero: lalein.S(z), One: lalein.S(o), Other: r}
	}
	l := lalein.New(map[string]*lalein.Translation{
		"apples": {
			Format: "%{main}",
			Parameters: map[string]*lalein.Parameter{
				"main": {
					ArgumentIndex: 1,
					One:           lalein.S("I have an apple."),
					Other:         "I have %d apples.",
				},
			},
		},
		"user_apples": {
			Format: "%{verb}",
			Parameters: map[string]*lalein.Parameter{
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
	l.SetPluralResolver(lalein.UsingLanguage("en"))

	cases := []struct {
		handler string
		args    []any
	}{
		{"apples", []any{0}},
		{"apples", []any{1}},
		{"apples", []any{5}},
		{"user_apples", []any{"female", 0}},
		{"user_apples", []any{"male", 1}},
		{"user_apples", []any{"other", 5}},
		{"user_apples", []any{nil, 2}},
	}
	for _, c := range cases {
		text, err := l.Format(c.handler, c.args...)
		if err != nil {
			fmt.Println("error:", err)
		} else {
			fmt.Println(text)
		}
	}
	// A couple of resolvers so the rule table isn't dead code.
	for _, lang := range []string{"ru", "ar", "fr", "sgs", "pt"} {
		category, ok := lalein.UsingLanguage(lang).FindType(21)
		fmt.Printf("%s: %s %v\n", lang, category, ok)
	}
}
