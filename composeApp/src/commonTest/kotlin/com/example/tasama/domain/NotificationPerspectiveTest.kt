package com.example.tasama.domain

import com.example.tasama.domain.model.Activity
import com.example.tasama.domain.model.ActivityCategory
import com.example.tasama.presentation.notifications.getActivityDescription
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationPerspectiveTest {

    @Test
    fun testInvitationSentPerspective() {
        val activity = Activity(
            id = "act_1",
            userId = "user_a",
            userName = "Alice",
            affectedUserId = "user_b",
            affectedUserName = "Bebet cerdas",
            category = ActivityCategory.SAVINGS,
            type = "INVITATION_SENT",
            metadata = mapOf("spaceName" to "Holiday Fund")
        )

        // Performer's perspective (Alice)
        val descriptionForAlice = getActivityDescription(activity, "user_a")
        assertEquals("You invited Bebet cerdas to Holiday Fund", descriptionForAlice)

        // Affected user's perspective (Bebet cerdas)
        val descriptionForBebet = getActivityDescription(activity, "user_b")
        assertEquals("Alice invited you to Holiday Fund", descriptionForBebet)

        // Third party's perspective (Charlie)
        val descriptionForCharlie = getActivityDescription(activity, "user_c")
        assertEquals("Alice invited Bebet cerdas to Holiday Fund", descriptionForCharlie)
    }

    @Test
    fun testMemberRemovedPerspective() {
        val activity = Activity(
            id = "act_2",
            userId = "user_a",
            userName = "Alice",
            affectedUserId = "user_b",
            affectedUserName = "Bob",
            category = ActivityCategory.SAVINGS,
            type = "MEMBER_REMOVED",
            metadata = mapOf("spaceName" to "Wedding Save")
        )

        // Alice removed Bob
        val descriptionForAlice = getActivityDescription(activity, "user_a")
        assertEquals("You removed Bob from Wedding Save", descriptionForAlice)

        // Bob was removed by Alice
        val descriptionForBob = getActivityDescription(activity, "user_b")
        assertEquals("You were removed from Wedding Save", descriptionForBob)

        // Charlie sees Alice removed Bob
        val descriptionForCharlie = getActivityDescription(activity, "user_c")
        assertEquals("Alice removed Bob from Wedding Save", descriptionForCharlie)
    }

    @Test
    fun testTransactionAddedPerspective() {
        val activity = Activity(
            id = "act_3",
            userId = "user_a",
            userName = "Alice",
            category = ActivityCategory.SAVINGS,
            type = "TRANSACTION_ADDED",
            metadata = mapOf("spaceName" to "House", "amount" to "Rp 500.000")
        )

        // Alice added contribution
        val descriptionForAlice = getActivityDescription(activity, "user_a")
        assertEquals("You added Rp 500.000 to House", descriptionForAlice)

        // Bob sees Alice added contribution
        val descriptionForBob = getActivityDescription(activity, "user_b")
        assertEquals("Alice added Rp 500.000 to House", descriptionForBob)
    }
}
