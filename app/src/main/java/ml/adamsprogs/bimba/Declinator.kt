package ml.adamsprogs.bimba

class Declinator {
    companion object {
        fun decline(number: Long): Int {
            return when {
                number == 0L -> R.string.now
                number % 10 == 0L -> R.string.departure_in__plural_genitive
                number == 1L -> R.string.departure_in__singular_genitive
                number in listOf<Long>(12,13,14) -> R.string.departure_in__plural_genitive
                number % 10 in listOf<Long>(2, 3, 4) -> R.string.departure_in__plural_nominative
                number % 10 in listOf<Long>(1,5,6,7,8,9) -> R.string.departure_in__plural_genitive
                else -> -1
            }
        }
    }
}