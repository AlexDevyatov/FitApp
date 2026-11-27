package ru.fitapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Пересчёт КБЖУ компонента: масса, добавленный сахар и разбор ввода.
 * База — числа модели, текущие значения всегда считаются от неё.
 */
class PortionRecalcTest {

    private val base = Nutrition(
        kcal = 200.0,
        proteinG = 10.0,
        fatG = 5.0,
        carbsG = 30.0,
        fiberG = 3.0,
        sugarG = 2.0,
    )

    @Test
    fun `mass edit scales all nutrients proportionally`() {
        val result = PortionRecalc.effective(base, baseGrams = 200.0, grams = 300.0)
        assertEquals(300.0, result.kcal, 1e-9)
        assertEquals(15.0, result.proteinG, 1e-9)
        assertEquals(7.5, result.fatG, 1e-9)
        assertEquals(45.0, result.carbsG, 1e-9)
        assertEquals(4.5, result.fiberG, 1e-9)
        assertEquals(3.0, result.sugarG, 1e-9)
    }

    @Test
    fun `mass smaller than base shrinks calories`() {
        val result = PortionRecalc.effective(base, baseGrams = 200.0, grams = 100.0)
        assertEquals(100.0, result.kcal, 1e-9)
        assertEquals(0.5, PortionRecalc.scaleFactor(200.0, 100.0), 1e-9)
    }

    @Test
    fun `unchanged mass keeps base numbers exactly`() {
        val result = PortionRecalc.effective(base, baseGrams = 200.0, grams = 200.0)
        assertEquals(base, result)
    }

    @Test
    fun `missing base mass leaves numbers untouched`() {
        // Модель вес не оценила: пересчитывать не от чего, поэтому база остаётся базой.
        assertFalse(PortionRecalc.canEditGrams(null))
        val result = PortionRecalc.effective(base, baseGrams = null, grams = 300.0)
        assertEquals(base, result)
        assertEquals(1.0, PortionRecalc.scaleFactor(null, 300.0), 1e-9)
    }

    @Test
    fun `zero base mass does not divide by zero`() {
        val result = PortionRecalc.effective(base, baseGrams = 0.0, grams = 100.0)
        assertEquals(base, result)
        assertFalse(PortionRecalc.canEditGrams(0.0))
    }

    @Test
    fun `sugar adds ten grams of carbs and forty kcal`() {
        val result = PortionRecalc.effective(
            base,
            baseGrams = 200.0,
            grams = 200.0,
            addedSugarG = PortionRecalc.ADDED_SUGAR_G,
        )
        assertEquals(240.0, result.kcal, 1e-9)
        assertEquals(12.0, result.sugarG, 1e-9)
        assertEquals(40.0, result.carbsG, 1e-9)
        // Белок и жир сахар не меняет.
        assertEquals(base.proteinG, result.proteinG, 1e-9)
        assertEquals(base.fatG, result.fatG, 1e-9)
    }

    @Test
    fun `sugar and mass edit combine`() {
        val result = PortionRecalc.effective(
            base,
            baseGrams = 200.0,
            grams = 100.0,
            addedSugarG = PortionRecalc.ADDED_SUGAR_G,
        )
        // Половина порции (100 ккал) плюс сахар (40 ккал).
        assertEquals(140.0, result.kcal, 1e-9)
        assertEquals(11.0, result.sugarG, 1e-9)
    }

    @Test
    fun `zero sugar keeps numbers unchanged`() {
        val result = PortionRecalc.effective(base, baseGrams = 200.0, grams = 200.0, addedSugarG = 0.0)
        assertEquals(base, result)
    }

    @Test
    fun `parse grams accepts dot and comma`() {
        assertEquals(150.0, PortionRecalc.parseGrams("150")!!, 1e-9)
        assertEquals(150.5, PortionRecalc.parseGrams("150,5")!!, 1e-9)
        assertEquals(150.5, PortionRecalc.parseGrams(" 150.5 ")!!, 1e-9)
    }

    @Test
    fun `parse grams rejects empty garbage and out of range`() {
        assertNull(PortionRecalc.parseGrams(""))
        assertNull(PortionRecalc.parseGrams("   "))
        assertNull(PortionRecalc.parseGrams("много"))
        assertNull(PortionRecalc.parseGrams("0"))
        assertNull(PortionRecalc.parseGrams("-50"))
        assertNull(PortionRecalc.parseGrams("5001"))
        assertNull(PortionRecalc.parseGrams("NaN"))
    }

    @Test
    fun `parse grams keeps borders inclusive`() {
        assertEquals(PortionRecalc.MIN_GRAMS, PortionRecalc.parseGrams("1")!!, 1e-9)
        assertEquals(PortionRecalc.MAX_GRAMS, PortionRecalc.parseGrams("5000")!!, 1e-9)
    }

    @Test
    fun `grams to input drops trailing zero and keeps fraction`() {
        assertEquals("150", PortionRecalc.gramsToInput(150.0))
        assertEquals("150,5", PortionRecalc.gramsToInput(150.5))
        assertEquals("", PortionRecalc.gramsToInput(null))
    }

    @Test
    fun `changing mass twice always counts from model base`() {
        // Репозиторий хранит базу модели отдельно, поэтому 200 -> 350 -> 200
        // возвращает ровно исходные числа, а не накопленную погрешность.
        val at350 = PortionRecalc.effective(base, baseGrams = 200.0, grams = 350.0)
        val backTo200 = PortionRecalc.effective(base, baseGrams = 200.0, grams = 200.0)
        assertEquals(base.kcal * 1.75, at350.kcal, 1e-9)
        assertEquals(base, backTo200)
    }
}