const { onCall, HttpsError } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const { GoogleGenerativeAI } = require("@google/generative-ai");

admin.initializeApp();

// ─── Get total user count from Firebase Auth ────────────────────────────────
exports.getUserCount = onCall(
  { region: "us-central1" },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "You must be signed in.");
    }

    try {
      let count = 0;
      let nextPageToken;
      do {
        const listResult = await admin.auth().listUsers(1000, nextPageToken);
        count += listResult.users.length;
        nextPageToken = listResult.pageToken;
      } while (nextPageToken);

      return { count };
    } catch (err) {
      console.error("getUserCount error:", err);
      throw new HttpsError("internal", "Failed to get user count.");
    }
  }
);

// FREE Google AI API key (from AI Studio - no billing needed)
const API_KEY = "AIzaSyBCCIjFC_5E16FQSD3Yl8xKL67kc50cqJQ";

const genAI = new GoogleGenerativeAI(API_KEY);

exports.summarizePdf = onCall(
  { region: "us-central1", timeoutSeconds: 120, memory: "1GiB" },
  async (request) => {
    const { text, fileBase64, mimeType, isChat } = request.data;

    if (!text && !fileBase64) {
      throw new HttpsError("invalid-argument", "Text or a file is required.");
    }

    try {
      const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });

      const systemInstruction =
        "You are the AI Assistant for 'ScanToPdf', developed by Hrishikesh Giri. " +
        "Be professional and concise.";

      const promptText = isChat
        ? `${systemInstruction}\n\nUser Question: ${text || "Analyze this file."}`
        : `${systemInstruction}\n\nPlease provide a clear bulleted summary:\n\n${text || ""}`;

      // Build parts array
      const parts = [{ text: promptText }];

      if (fileBase64 && mimeType) {
        parts.push({
          inlineData: {
            data: fileBase64,
            mimeType: mimeType,
          },
        });
      }

      const result = await model.generateContent({
        contents: [{ role: "user", parts: parts }],
      });

      const response = result.response;
      const output =
        response.candidates?.[0]?.content?.parts?.[0]?.text ||
        "I couldn't process that.";

      return { summary: output };
    } catch (err) {
      console.error("AI Error:", err);
      throw new HttpsError(
        "internal",
        "AI failed to process. " + (err.message || "Check if file size is too large.")
      );
    }
  }
);

// ─── Admin Push Notification Function ───────────────────────────────────────
exports.sendNotification = onCall(
  { region: "us-central1" },
  async (request) => {
    // 1. Verify the caller is authenticated
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "You must be signed in.");
    }

    const callerEmail = request.auth.token.email;
    if (!callerEmail) {
      throw new HttpsError("permission-denied", "No email associated with this account.");
    }

    // 2. Check if caller is an admin
    try {
      const adminDoc = await admin.firestore()
        .collection("admins")
        .doc(callerEmail)
        .get();

      if (!adminDoc.exists) {
        throw new HttpsError("permission-denied", "You are not authorized to send notifications.");
      }
    } catch (err) {
      if (err instanceof HttpsError) throw err;
      console.error("Admin check error:", err);
      throw new HttpsError("internal", "Failed to verify admin status.");
    }

    // 3. Validate input
    const { title, message } = request.data;
    if (!title || !message) {
      throw new HttpsError("invalid-argument", "Title and message are required.");
    }

    // 4. Send FCM message to all_users topic
    try {
      const fcmMessage = {
        topic: "all_users",
        notification: {
          title: title,
          body: message,
        },
        data: {
          title: title,
          message: message,
          timestamp: Date.now().toString(),
        },
        android: {
          priority: "high",
          notification: {
            channelId: "admin_notifications",
            priority: "high",
            defaultSound: true,
          },
        },
      };

      const fcmResponse = await admin.messaging().send(fcmMessage);
      console.log("FCM sent:", fcmResponse);

      // 5. Save notification to Firestore for history
      await admin.firestore().collection("notifications").add({
        title: title,
        message: message,
        sentBy: callerEmail,
        timestamp: Date.now(),
        messageId: fcmResponse,
      });

      return { success: true, messageId: fcmResponse };
    } catch (err) {
      console.error("FCM Error:", err);
      throw new HttpsError("internal", "Failed to send notification: " + err.message);
    }
  }
);
