package ru.fitapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TdeeTest {

    private val delta = 1e-6

    @Test
    fun `bmr male Mifflin-St Jeor`() {
        // 10*80 + 6.25*180 - 5*30 + 5 = 1780
        assertEquals(1780.0, Tdee.bmr(Sex.MALE, 80.0, 180.0, 30), delta)
    }

    @Test
    fun `bmr female Mifflin-St Jeor`() {
        // 10*55 + 6.25*165 - 5*25 - 161 = 1295.25
        assertEquals(1295.25, Tdee.bmr(Sex.FEMALE, 55.0, 165.0, 25), delta)
    }

    @Test
    fun `tdee is bmr times activity`() {
        assertEquals(1780.0 * 1.375, Tdee.tdee(1780.0, Activity.LIGHT.factor), delta)
    }

    @Test
    fun `target kcal with 20 percent deficit`() {
        val goals = Tdee.goals(Sex.MALE, 80.0, 180.0, 30, Activity.LIGHT.factor, deficitPct = 20)
        // tdee = 2447.5, цель = 2447.5 * 0.8 = 1958
        assertEquals(1958, goals.targets.kcal)
        assertFalse(goals.raisedToSafetyFloor)
    }

    @Test
    fun `deficit is clamped to 15-20 percent`() {
        val tooSmall = Tdee.goals(Sex.MALE, 80.0, 180.0, 30, Activity.LIGHT.factor, deficitPct = 5)
        val normal15 = Tdee.goals(Sex.MALE, 80.0, 180.0, 30, Activity.LIGHT.factor, deficitPct = 15)
        assertEquals(normal15.targets.kcal, tooSmall.targets.kcal)

        val tooBig = Tdee.goals(Sex.MALE, 80.0, 180.0, 30, Activity.LIGHT.factor, deficitPct = 50)
        val normal20 = Tdee.goals(Sex.MALE, 80.0, 180.0, 30, Activity.LIGHT.factor, deficitPct = 20)
        assertEquals(normal20.targets.kcal, tooBig.targets.kcal)
    }

    @Test
    fun `female target below 1200 raised to safety floor`() {
        // bmr = 10*45 + 6.25*150 - 5*50 - 161 = 976.5; tdee = 1171.8; raw = 937.44 < 1200
        val goals = Tdee.goals(Sex.FEMALE, 45.0, 150.0, 50, Activity.SEDENTARY.factor, deficitPct = 20)
        assertTrue(goals.raisedToSafetyFloor)
        assertEquals(Tdee.SAFETY_FLOOR_FEMALE_KCAL, goals.targets.kcal)
    }

    @Test
    fun `male target below 1500 raised to safety floor`() {
        // bmr = 10*50 + 6.25*160 - 5*70 + 5 = 1155; tdee = 1386; raw(15%) = 1178.1 < 1500
        val goals = Tdee.goals(Sex.MALE, 50.0, 160.0, 70, Activity.SEDENTARY.factor, deficitPct = 15)
        assertTrue(goals.raisedToSafetyFloor)
        assertEquals(Tdee.SAFETY_FLOOR_MALE_KCAL, goals.targets.kcal)
    }

    @Test
    fun `male target above floor is not raised`() {
        // bmr = 10*60 + 6.25*170 - 5*30 + 5 = 1517.5; tdee = 1821; raw(15%) = 1547.85 > 1500
        val goals = Tdee.goals(Sex.MALE, 60.0, 170.0, 30, Activity.SEDENTARY.factor, deficitPct = 15)
        assertFalse(goals.raisedToSafetyFloor)
        assertEquals(1548, goals.targets.kcal)
    }

    @Test
    fun `macros protein 1_6 per kg, fat 27 percent kcal, carbs remainder`() {
        val goals = Tdee.goals(Sex.MALE, 80.0, 180.0, 30, Activity.LIGHT.factor, deficitPct = 20)
        val kcal = goals.targets.kcal // 1958
        assertEquals(128, goals.targets.proteinG) // 1.6 * 80
        assertEquals(59, goals.targets.fatG) // round(1958*0.27/9) = round(58.74)
        // углеводы — остаток: (1958 - 128*4 - 59*9)/4 = (1958 - 512 - 531)/4 = 228.75 → 229
        assertEquals(229, goals.targets.carbsG)
        assertTrue(kcal > 0)
    }
}
