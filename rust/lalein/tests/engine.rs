use lalein::{Arg, Lalein, Parameter, PluralResolver, PluralType, Translation};

fn param(idx: usize, z: Option<&str>, o: Option<&str>, t: Option<&str>, r: &str) -> Parameter {
    Parameter {
        argument_index: idx,
        zero: z.map(str::to_string),
        one: o.map(str::to_string),
        two: t.map(str::to_string),
        other: r.to_string(),
        ..Default::default()
    }
}

fn lalein_with(p: Parameter, lang: &str) -> Lalein {
    let mut l = Lalein::new(vec![(
        "n".to_string(),
        Translation {
            format: "%{p}".to_string(),
            parameters: vec![("p".to_string(), p)],
        },
    )]);
    l.set_plural_resolver(PluralResolver::for_language(lang));
    l
}

#[test]
fn english_like() {
    let l = lalein_with(param(1, Some("ZERO"), Some("ONE"), None, "OTHER"), "en");
    assert_eq!(l.format("n", &[Arg::Int(0)]).unwrap(), "ZERO");
    assert_eq!(l.format("n", &[Arg::Int(1)]).unwrap(), "ONE");
    assert_eq!(l.format("n", &[Arg::Int(2)]).unwrap(), "OTHER");
    assert_eq!(l.format("n", &[Arg::Int(5)]).unwrap(), "OTHER");
    assert_eq!(l.format("n", &[Arg::Float(0.5)]).unwrap(), "OTHER");
}

#[test]
fn negative_values_use_absolute_value_category() {
    let l = lalein_with(
        param(1, Some("ZERO"), Some("ONE"), Some("TWO"), "OTHER"),
        "en",
    );
    assert_eq!(l.format("n", &[Arg::Int(-1)]).unwrap(), "ONE");
    assert_eq!(l.format("n", &[Arg::Int(-2)]).unwrap(), "TWO");
    assert_eq!(l.format("n", &[Arg::Int(-5)]).unwrap(), "OTHER");
    assert_eq!(l.format("n", &[Arg::Float(-1.5)]).unwrap(), "OTHER");
}

#[test]
fn zero_maps_to_one_punjabi() {
    // pa: n == 0|1 -> ONE; without z defined, 0 falls back to the rule
    let l = lalein_with(param(1, None, Some("ONE"), None, "OTHER"), "pa");
    assert_eq!(l.format("n", &[Arg::Int(0)]).unwrap(), "ONE");
    assert_eq!(l.format("n", &[Arg::Int(1)]).unwrap(), "ONE");
    assert_eq!(l.format("n", &[Arg::Int(3)]).unwrap(), "OTHER");
}

#[test]
fn russian_few_many() {
    let r = PluralResolver::for_language("ru");
    assert_eq!(r.find_type(1.0), Some(PluralType::One));
    assert_eq!(r.find_type(21.0), Some(PluralType::One));
    assert_eq!(r.find_type(22.0), Some(PluralType::Few));
    assert_eq!(r.find_type(11.0), Some(PluralType::Many));
    assert_eq!(r.find_type(-21.0), Some(PluralType::One));
}

#[test]
fn pt_pt_differs_from_pt() {
    // pt: 0 -> one; pt-PT: 0 -> other. Both: many at exact millions.
    let br = PluralResolver::for_language("pt");
    let eu = PluralResolver::for_locale("pt", "PT");
    assert_eq!(br.find_type(0.0), Some(PluralType::One));
    assert_eq!(eu.find_type(0.0), None);
    assert_eq!(br.find_type(1_000_000.0), Some(PluralType::Many));
    assert_eq!(eu.find_type(1_000_000.0), Some(PluralType::Many));
}

#[test]
fn nan_and_infinity_fall_to_other() {
    let l = lalein_with(param(1, Some("ZERO"), Some("ONE"), None, "OTHER"), "en");
    assert_eq!(l.format("n", &[Arg::Float(f64::NAN)]).unwrap(), "OTHER");
    assert_eq!(l.format("n", &[Arg::Float(f64::INFINITY)]).unwrap(), "OTHER");
}

#[test]
fn select_mode_gender() {
    let p = Parameter {
        argument_index: 1,
        other: "They liked your post".to_string(),
        custom: vec![
            ("female".to_string(), "She liked your post".to_string()),
            ("male".to_string(), "He liked your post".to_string()),
        ],
        ..Default::default()
    };
    let l = lalein_with(p, "en");
    assert_eq!(l.format("n", &[Arg::Str("female")]).unwrap(), "She liked your post");
    assert_eq!(l.format("n", &[Arg::Str("male")]).unwrap(), "He liked your post");
    assert_eq!(l.format("n", &[Arg::Str("other")]).unwrap(), "They liked your post");
    assert_eq!(l.format("n", &[Arg::None]).unwrap(), "They liked your post");
}

#[test]
fn nested_gender_and_count() {
    // The README's user_apples example: select-mode wrapping plural counts.
    let count = |z: &'static str, o: &'static str, r: &'static str| Parameter {
        argument_index: 2,
        zero: Some(z.to_string()),
        one: Some(o.to_string()),
        other: r.to_string(),
        ..Default::default()
    };
    let translation = Translation {
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
                count("She doesn't have apples", "She has 1 apple", "She has %2$d apples"),
            ),
            (
                "male_count".to_string(),
                count("He doesn't have apples", "He has 1 apple", "He has %2$d apples"),
            ),
            (
                "other_count".to_string(),
                count("They don't have apples", "They have 1 apple", "They have %2$d apples"),
            ),
        ],
    };
    let mut l = Lalein::new(vec![("user_apples".to_string(), translation)]);
    l.set_plural_resolver(PluralResolver::for_language("en"));
    assert_eq!(
        l.format("user_apples", &[Arg::Str("female"), Arg::Int(0)]).unwrap(),
        "She doesn't have apples"
    );
    assert_eq!(
        l.format("user_apples", &[Arg::Str("female"), Arg::Int(5)]).unwrap(),
        "She has 5 apples"
    );
    assert_eq!(
        l.format("user_apples", &[Arg::Str("male"), Arg::Int(1)]).unwrap(),
        "He has 1 apple"
    );
    assert_eq!(
        l.format("user_apples", &[Arg::Str("other"), Arg::Int(0)]).unwrap(),
        "They don't have apples"
    );
}

#[test]
fn counter_is_not_the_first_argument() {
    // README case B: %2$d inside the forms marks argument 2 as the counter.
    let translation = Translation {
        format: "%{main}".to_string(),
        parameters: vec![(
            "main".to_string(),
            Parameter {
                argument_index: 2,
                one: Some("Cash payment of %1$s saved (1 month allocated).".to_string()),
                other: "Cash payment of %1$s saved (%2$d months allocated).".to_string(),
                ..Default::default()
            },
        )],
    };
    let mut l = Lalein::new(vec![("payment".to_string(), translation)]);
    l.set_plural_resolver(PluralResolver::for_language("en"));
    assert_eq!(
        l.format("payment", &[Arg::Str("12.34"), Arg::Int(1)]).unwrap(),
        "Cash payment of 12.34 saved (1 month allocated)."
    );
    assert_eq!(
        l.format("payment", &[Arg::Str("12.34"), Arg::Int(7)]).unwrap(),
        "Cash payment of 12.34 saved (7 months allocated)."
    );
}

#[test]
fn unknown_handler_is_used_as_template() {
    let l = Lalein::new(vec![]);
    assert_eq!(l.format("I have %d apples", &[Arg::Int(5)]).unwrap(), "I have 5 apples");
    assert_eq!(l.format("100%%", &[]).unwrap(), "100%");
}

#[test]
fn printf_positional_and_types() {
    let l = Lalein::new(vec![]);
    assert_eq!(
        l.format("%2$d of %1$d items", &[Arg::Int(3), Arg::Int(5)]).unwrap(),
        "5 of 3 items"
    );
    assert_eq!(
        l.format("%s has %d (%f-ish, %x)", &[Arg::Str("x"), Arg::Int(7), Arg::Float(2.5), Arg::Int(255)]).unwrap(),
        "x has 7 (2.500000-ish, ff)"
    );
    assert_eq!(l.format("%s", &[Arg::Float(5.0)]).unwrap(), "5.0");
}

#[test]
fn missing_parameter_is_an_error() {
    let l = Lalein::new(vec![(
        "n".to_string(),
        Translation {
            format: "%{missing}".to_string(),
            parameters: vec![("p".to_string(), param(1, None, None, None, "OTHER"))],
        },
    )]);
    let err = l.format("n", &[Arg::Int(1)]).unwrap_err();
    assert!(err.to_string().contains("Unable to locate localization parameter 'missing' in 'n'"));
}
