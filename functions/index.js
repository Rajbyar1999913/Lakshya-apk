const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { onRequest } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");

initializeApp();

const releaseWebhookSecret = defineSecret("RELEASE_WEBHOOK_SECRET");
const officialApkPrefix =
  "https://github.com/Rajbyar1999913/Lakshya-apk/releases/download/";

exports.publishRelease = onRequest(
  {
    region: "asia-south1",
    secrets: [releaseWebhookSecret]
  },
  async (request, response) => {
    if (request.method !== "POST") {
      response.set("Allow", "POST").status(405).json({ error: "Method not allowed" });
      return;
    }

    const suppliedSecret = request.get("authorization")?.replace(/^Bearer\s+/i, "");
    if (!suppliedSecret || suppliedSecret !== releaseWebhookSecret.value()) {
      response.status(401).json({ error: "Unauthorized" });
      return;
    }

    const versionCode = Number(request.body?.versionCode);
    const versionName = String(request.body?.versionName ?? "").trim();
    const updateUrl = String(request.body?.updateUrl ?? "").trim();
    const message = String(request.body?.message ?? "").trim();

    if (!Number.isInteger(versionCode) || versionCode < 1) {
      response.status(400).json({ error: "versionCode must be a positive integer" });
      return;
    }

    if (!versionName || !updateUrl.startsWith(officialApkPrefix)) {
      response.status(400).json({ error: "Invalid release details" });
      return;
    }

    const updateMessage = message || `Lakshya ${versionName} is available. Please update now.`;
    await getFirestore().collection("app_config").doc("main").set(
      {
        latestVersionCode: versionCode,
        minimumVersionCode: versionCode,
        forceUpdate: true,
        updateUrl,
        updateMessage,
        releaseUpdatedAt: Date.now()
      },
      { merge: true }
    );

    const notificationId = await getMessaging().send({
      topic: "lakshya_app_updates",
      notification: {
        title: "Lakshya update available",
        body: updateMessage
      },
      data: {
        versionCode: String(versionCode),
        versionName,
        updateUrl
      },
      android: {
        priority: "high",
        notification: {
          channelId: "lakshya_app_updates"
        }
      }
    });

    logger.info("Release published", { versionCode, versionName, notificationId });
    response.status(200).json({ ok: true, notificationId });
  }
);
