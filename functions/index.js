const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
admin.initializeApp();

exports.onMessageCreated = functions.region("asia-southeast2").firestore
    .document("chat_channels/{channelId}/messages/{messageId}")
    .onCreate(async (snapshot, context) => {
        const message = snapshot.data();
        if (!message) return null;

        const channelId = context.params.channelId;
        const messageId = context.params.messageId;
        const senderId = message.userId;

        try {
            // 1. Get sender info (name & profile picture)
            const senderDoc = await admin.firestore().collection("users").doc(senderId).get();
            const senderData = senderDoc.data();
            const senderName = senderData?.displayName || message.senderName || "Someone";
            const senderPhoto = senderData?.profilePictureUrl || null;
            const senderAvatar = senderData?.avatar || null;

            // 2. Get the channel to find recipients
            const channelDoc = await admin.firestore().collection("chat_channels").doc(channelId).get();
            if (!channelDoc.exists) return null;

            const participantIds = channelDoc.data().participantIds || [];
            const recipients = participantIds.filter(id => id !== senderId);

            const tasks = recipients.map(async (uid) => {
                const userDoc = await admin.firestore().collection("users").doc(uid).get();
                const token = userDoc.data()?.fcmToken;
                if (!token || token.trim() === "") return null;

                const payload = {
                    token: token,
                    data: {
                        channelId: channelId,
                        messageId: messageId,
                        senderId: senderId,
                        sender_name: senderName,
                        sender_photo: senderPhoto || "",
                        sender_avatar: senderAvatar || "",
                        type: "NEW_MESSAGE",
                        title: senderName,
                        body: message.text || "New message",
                    },
                    android: {
                        priority: "high",
                    },
                    apns: {
                        payload: {
                            aps: {
                                category: "NEW_MESSAGE_CATEGORY", // Important for iOS Reply actions
                                "mutable-content": 1,
                                sound: "default",
                            }
                        }
                    }
                };

                return admin.messaging().send(payload)
                    .then(() => console.log(`Sent to ${uid}`))
                    .catch(async (err) => {
                        console.error(`Error sending to ${uid}:`, err);
                        // If token is invalid/expired, remove it
                        if (err.code === "messaging/registration-token-not-registered" ||
                            err.code === "messaging/invalid-registration-token") {
                            console.log(`Cleaning up invalid token for ${uid}`);
                            await admin.firestore().collection("users").doc(uid).update({ fcmToken: admin.firestore.FieldValue.delete() });
                        }
                    });
            });

            await Promise.all(tasks);
        } catch (error) {
            console.error("Execution error:", error);
        }
        return null;
    });

exports.onNotificationCreated = functions.region("asia-southeast2").firestore
    .document("notifications/{notificationId}")
    .onCreate(async (snapshot, context) => {
        const notification = snapshot.data();
        if (!notification) return null;

        const targetUid = notification.targetUid;
        const title = notification.title || "Tasama";
        const body = notification.body || "";

        try {
            const userDoc = await admin.firestore().collection("users").doc(targetUid).get();
            const token = userDoc.data()?.fcmToken;

            if (!token || token.trim() === "") {
                console.log(`No FCM token for user ${targetUid}`);
                return null;
            }

            const payload = {
                token: token,
                notification: {
                    title: title,
                    body: body,
                },
                data: {
                    type: notification.type || "GEOFENCE",
                },
                android: {
                    priority: "high",
                    notification: {
                        sound: "default",
                        clickAction: "FLUTTER_NOTIFICATION_CLICK"
                    }
                }
            };

            await admin.messaging().send(payload);
            console.log(`Notification sent to ${targetUid}`);
        } catch (error) {
            console.error("Error sending geofence notification:", error);
        }
        return null;
    });

exports.onSavingsInvitationCreated = functions.region("asia-southeast2").firestore
    .document("savings_invitations/{invitationId}")
    .onCreate(async (snapshot, context) => {
        const invitation = snapshot.data();
        if (!invitation || invitation.status !== "PENDING") return null;

        const targetUid = invitation.inviteeId;
        const inviterName = invitation.inviterName;
        const spaceName = invitation.spaceName;

        try {
            const userDoc = await admin.firestore().collection("users").doc(targetUid).get();
            const token = userDoc.data()?.fcmToken;

            if (!token || token.trim() === "") {
                console.log(`No FCM token for user ${targetUid}`);
                return null;
            }

            const payload = {
                token: token,
                notification: {
                    title: "New Savings Invitation",
                    body: `${inviterName} invited you to join "${spaceName}"`,
                },
                data: {
                    type: "SAVINGS_INVITATION",
                    spaceId: invitation.spaceId,
                    invitationId: context.params.invitationId,
                },
                android: {
                    priority: "high",
                }
            };

            await admin.messaging().send(payload);
            console.log(`Invitation notification sent to ${targetUid}`);
        } catch (error) {
            console.error("Error sending invitation notification:", error);
        }
        return null;
    });

exports.onSavingsActivityCreated = functions.region("asia-southeast2").firestore
    .document("savings_spaces/{spaceId}/activities/{activityId}")
    .onCreate(async (snapshot, context) => {
        const activity = snapshot.data();
        if (!activity) return null;

        const spaceId = context.params.spaceId;
        const actorId = activity.userId;
        const type = activity.type;
        const details = activity.details;

        // Only notify for specific activity types
        const notifyTypes = [
            "MEMBER_JOINED",
            "MEMBER_LEFT",
            "MEMBER_REMOVED",
            "OWNERSHIP_TRANSFERRED",
            "TRANSACTION_ADDED"
        ];

        if (!notifyTypes.includes(type)) return null;

        try {
            const spaceDoc = await admin.firestore().collection("savings_spaces").doc(spaceId).get();
            if (!spaceDoc.exists) return null;

            const spaceData = spaceDoc.data();
            const memberIds = spaceData.memberIds || [];
            const spaceName = spaceData.name;

            // Recipients are all members except the actor
            const recipients = memberIds.filter(id => id !== actorId);

            const tasks = recipients.map(async (uid) => {
                const userDoc = await admin.firestore().collection("users").doc(uid).get();
                const token = userDoc.data()?.fcmToken;
                if (!token || token.trim() === "") return null;

                const payload = {
                    token: token,
                    notification: {
                        title: spaceName,
                        body: `${activity.userName}: ${details}`,
                    },
                    data: {
                        type: "SAVINGS_ACTIVITY",
                        spaceId: spaceId,
                        activityType: type,
                    },
                    android: {
                        priority: "high",
                    }
                };

                return admin.messaging().send(payload)
                    .catch(err => console.error(`Error sending activity to ${uid}:`, err));
            });

            await Promise.all(tasks);
        } catch (error) {
            console.error("Error sending activity notifications:", error);
        }
        return null;
    });
