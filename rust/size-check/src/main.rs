//! Size harness. Both configurations print the same kind of output; the
//! `with-lalein` feature exercises a realistic slice of the library
//! (plurals, select-mode, nested references, printf) so dead-code
//! elimination keeps only what an actual application would keep.

fn main() {
    #[cfg(feature = "with-lalein")]
    {
        use lalein::{Arg, Lalein, Parameter, PluralResolver, Translation};

        let nested = Translation {
            format: "%{verb}".to_string(),
            parameters: vec![
                (
                    "verb".to_string(),
                    Parameter {
                        argument_index: 1,
                        other: "%{other_count}".to_string(),
                        custom: vec![
                            ("female".to_string(), "%{female_count}".to_string()),
                            ("male".to_string(), "%{male_count}".to_string()),
                        ],
                        ..Default::default()
                    },
                ),
                (
                    "female_count".to_string(),
                    Parameter {
                        argument_index: 2,
                        zero: Some("She doesn't have apples".to_string()),
                        one: Some("She has 1 apple".to_string()),
                        other: "She has %2$d apples".to_string(),
                        ..Default::default()
                    },
                ),
                (
                    "male_count".to_string(),
                    Parameter {
                        argument_index: 2,
                        zero: Some("He doesn't have apples".to_string()),
                        one: Some("He has 1 apple".to_string()),
                        other: "He has %2$d apples".to_string(),
                        ..Default::default()
                    },
                ),
                (
                    "other_count".to_string(),
                    Parameter {
                        argument_index: 2,
                        zero: Some("They don't have apples".to_string()),
                        one: Some("They have 1 apple".to_string()),
                        other: "They have %2$d apples".to_string(),
                        ..Default::default()
                    },
                ),
            ],
        };
        let apples = Translation {
            format: "%{main}".to_string(),
            parameters: vec![(
                "main".to_string(),
                Parameter {
                    argument_index: 1,
                    one: Some("I have an apple.".to_string()),
                    other: "I have %d apples.".to_string(),
                    ..Default::default()
                },
            )],
        };
        let mut lalein = Lalein::new(vec![
            ("user_apples".to_string(), nested),
            ("apples".to_string(), apples),
        ]);
        lalein.set_plural_resolver(PluralResolver::for_language("en"));

        for (handler, args) in [
            ("apples", vec![Arg::Int(0)]),
            ("apples", vec![Arg::Int(1)]),
            ("apples", vec![Arg::Int(5)]),
            ("user_apples", vec![Arg::Str("female"), Arg::Int(0)]),
            ("user_apples", vec![Arg::Str("male"), Arg::Int(1)]),
            ("user_apples", vec![Arg::Str("other"), Arg::Int(5)]),
            ("user_apples", vec![Arg::None, Arg::Int(2)]),
        ] {
            match lalein.format(handler, &args) {
                Ok(text) => println!("{text}"),
                Err(e) => println!("error: {e}"),
            }
        }
        // A couple of resolvers so the rule table isn't fully eliminated.
        for lang in ["ru", "ar", "fr", "sgs", "pt"] {
            let r = PluralResolver::for_language(lang);
            println!("{lang}: {:?}", r.find_type(21.0));
        }
    }
    #[cfg(not(feature = "with-lalein"))]
    {
        println!("lalein size baseline");
    }
}
