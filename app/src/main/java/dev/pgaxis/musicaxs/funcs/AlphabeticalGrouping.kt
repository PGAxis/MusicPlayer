package dev.pgaxis.musicaxs.funcs

import java.text.Collator
import java.text.Normalizer

private val collator: Collator = Collator.getInstance().apply {
    strength = Collator.SECONDARY
}

fun groupKey(text: String): String {
    val first = text.firstOrNull() ?: return "#"
    val base = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).first()
    return if (base.isLetter()) base.uppercaseChar().toString() else "#"
}

fun <T, R> groupAlphabetically(
    items: List<T>,
    nameOf: (T) -> String,
    makeHeader: (String) -> R,
    makeItem: (T) -> R
): Pair<List<R>, Map<String, Int>> {
    val result = mutableListOf<R>()
    val letterIndex = mutableMapOf<String, Int>()

    items
        .groupBy { groupKey(nameOf(it)) }
        .toSortedMap(compareBy { if (it == "#") "" else it })
        .forEach { (letter, group) ->
            letterIndex[letter] = result.size
            result.add(makeHeader(letter))
            group.sortedWith(compareBy(collator, nameOf))
                .forEach { result.add(makeItem(it)) }
        }

    return result to letterIndex
}

fun <T> List<T>.sortedAlphabetically(nameOf: (T) -> String): List<T> =
    sortedWith(compareBy(collator, nameOf))