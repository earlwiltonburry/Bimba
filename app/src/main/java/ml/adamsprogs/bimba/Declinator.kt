package ml.adamsprogs.bimba

class Declinator {
    companion object {
        fun decline(number: Long): Int {
            when {
                number == 0L -> return R.string.now
                number % 10 == 0L -> return R.string.departure_in__plural_genitive
                number == 1L -> return R.string.departure_in__singular_genitive
                number in listOf<Long>(12,13,14) -> return R.string.departure_in__plural_genitive
                number % 10 in listOf<Long>(2, 3, 4) -> return R.string.departure_in__plural_nominative
                number % 10 in listOf<Long>(1,5,6,7,8,9) -> return R.string.departure_in__plural_genitive
                else -> return -1
            }
        }
    }
}