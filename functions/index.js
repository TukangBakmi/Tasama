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

            // 2. Get the channel to find recipients
            const channelDoc = await admin.firestore().collection("chat_channels").doc(channelId).get();
            if (!channelDoc.exists) return null;

            const participantIds = channelDoc.data().participantIds || [];
            const recipients = participantIds.filter(id => id !== senderId);

            const tasks = recipients.map(async (uid) => {
                const userDoc = await admin.firestore().collection("users").doc(uid).get();
                const token = userDoc.data()?.fcmToken;
                if (!token) return null;

                const payload = {
                    token: token,
                    notification: {
                        title: senderName,
                        body: message.text || "New message",
                        image: senderPhoto, // Show profile picture/image if available
                    },
                    data: {
                        channelId: channelId,
                        messageId: messageId,
                        senderId: senderId,
                        type: "NEW_MESSAGE",
                        // This allows the app to know it should show 'Reply'/'Mark as Read'
                        click_action: "FLUTTER_NOTIFICATION_CLICK",
                    },
                    android: {
                        priority: "high",
                        notification: {
                            channelId: "chat_messages", // Important for Android Reply actions
                            icon: "stock_ticker_update",
                            color: "#075E54", // WhatsApp-like green
                            sound: "default",
                        }
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
                    .catch(err => console.error(`Error:`, err));
            });

            await Promise.all(tasks);
        } catch (error) {
            console.error("Execution error:", error);
        }
        return null;
    });