# Sample Fluent translation file used by the test-suite.
# Mirrors the canonical Localizable structure used across backends.

peaches = I have peaches.

apples = { $count ->
    [zero]  I don't have apples.
    [one]   I have an apple.
    [two]   I have two apples.
   *[other] I have { $count } apples.
}

baskets_with_oranges = { $baskets ->
    [zero] I don't have a basket { $oranges_zero_basket ->
        [zero]  or an orange
        [one]   but I have an orange
       *[other] but I have { $oranges_zero_basket } oranges
    }.
   *[other] I have { $baskets } baskets { $oranges ->
        [zero]  without oranges
        [one]   with one orange
       *[other] with { $oranges } oranges
    }.
}
