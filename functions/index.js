const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
admin.initializeApp();

// Deploying to asia-southeast2 to match the Firestore region
exports.onMessageCreated = functions.region("asia-southeast2").firestore
    .document("chat_channels/{channelId}/messages/{messageId}")
    .onCreate(async (snapshot, context) => {
        console.log("onMessageCreated v1.3 triggered");
        const message = snapshot.data();

        if (!message) {
            console.error("No data found in snapshot");
            return null;
        }

        const channelId = context.params.channelId;
        const senderId = message.userId;
        const senderName = message.senderName || "Someone";

        console.log(`Notification trigger: msg ${context.params.messageId} in ${channelId} from ${senderName} (${senderId})`);

        try {
            // Get the channel document to find participants
            const channelDoc = await admin.firestore().collection("chat_channels").doc(channelId).get();
            if (!channelDoc.exists) {
                console.log("Channel not found");
                return null;
            }

            const participantIds = channelDoc.data().participantIds || [];
            const recipients = participantIds.filter(id => id !== senderId);

            if (recipients.length === 0) {
                console.log("No recipients found");
                return null;
            }

            const tasks = recipients.map(async (uid) => {
                const userDoc = await admin.firestore().collection("users").doc(uid).get();
                const token = userDoc.data()?.fcmToken;

                if (!token) {
                    console.log(`No token for user ${uid}`);
                    return null;
                }

                const payload = {
                    token: token,
                    notification: {
                        title: senderName,
                        body: message.text || "New message",
                    },
                    data: {
                        channelId: channelId,
                        senderId: senderId
                    }
                };

                return admin.messaging().send(payload)
                    .then(() => console.log(`Sent notification to ${uid}`))
                    .catch(err => {
                        console.error(`Failed to send to ${uid}:`, err);
                        if (err.code === 'messaging/registration-token-not-registered') {
                            console.log(`Cleaning up stale token for ${uid}`);
                            return admin.firestore().collection("users").doc(uid).update({
                                fcmToken: admin.firestore.FieldValue.delete()
                            });
                        }
                    });
            });

            await Promise.all(tasks);
        } catch (error) {
            console.error("Execution error:", error.message);
            if (error.message.includes("PERMISSION_DENIED")) {
                console.error("CRITICAL: The Service Account lacks Firestore permissions. Please grant 'Cloud Datastore User' to tasama-aecc9@appspot.gserviceaccount.com in the GCP Console.");
            }
        }
        return null;
    });
