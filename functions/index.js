const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const {
  onRequest,
  onCall,
  HttpsError
} = require("firebase-functions/v2/https");
const { getAuth } = require("firebase-admin/auth");
const { defineSecret } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");

initializeApp();

const releaseWebhookSecret =
  defineSecret("RELEASE_WEBHOOK_SECRET");

const officialApkPrefix =
  "https://github.com/Rajbyar1999913/Lakshya-apk/releases/download/";

// =====================================================
// EXISTING RELEASE FUNCTION
// DO NOT CHANGE
// =====================================================

exports.publishRelease = onRequest(
  {
    region: "asia-south1",
    secrets: [releaseWebhookSecret]
  },
  async (request, response) => {

    if (request.method !== "POST") {
      response
        .set("Allow", "POST")
        .status(405)
        .json({
          error: "Method not allowed"
        });

      return;
    }

    const suppliedSecret =
      request
        .get("authorization")
        ?.replace(/^Bearer\s+/i, "");

    if (
      !suppliedSecret ||
      suppliedSecret !== releaseWebhookSecret.value()
    ) {
      response
        .status(401)
        .json({
          error: "Unauthorized"
        });

      return;
    }

    const versionCode =
      Number(request.body?.versionCode);

    const versionName =
      String(
        request.body?.versionName ?? ""
      ).trim();

    const updateUrl =
      String(
        request.body?.updateUrl ?? ""
      ).trim();

    const message =
      String(
        request.body?.message ?? ""
      ).trim();

    if (
      !Number.isInteger(versionCode) ||
      versionCode < 1
    ) {
      response
        .status(400)
        .json({
          error:
            "versionCode must be a positive integer"
        });

      return;
    }

    if (
      !versionName ||
      !updateUrl.startsWith(
        officialApkPrefix
      )
    ) {
      response
        .status(400)
        .json({
          error:
            "Invalid release details"
        });

      return;
    }

    const updateMessage =
      message ||
      `Lakshya ${versionName} is available. Please update now.`;

    await getFirestore()
      .collection("app_config")
      .doc("main")
      .set(
        {
          latestVersionCode:
            versionCode,

          minimumVersionCode:
            versionCode,

          forceUpdate:
            true,

          updateUrl:
            updateUrl,

          updateMessage:
            updateMessage,

          releaseUpdatedAt:
            Date.now()
        },
        {
          merge: true
        }
      );

    const notificationId =
      await getMessaging().send({

        topic:
          "lakshya_app_updates",

        notification: {
          title:
            "Lakshya update available",

          body:
            updateMessage
        },

        data: {
          versionCode:
            String(versionCode),

          versionName:
            versionName,

          updateUrl:
            updateUrl
        },

        android: {
          priority:
            "high",

          notification: {
            channelId:
              "lakshya_app_updates"
          }
        }
      });

    logger.info(
      "Release published",
      {
        versionCode,
        versionName,
        notificationId
      }
    );

    response
      .status(200)
      .json({
        ok: true,
        notificationId
      });
  }
);


// =====================================================
// MASTER PASSWORD RECOVERY
// MOBILE OTP VERIFIED USER
// =====================================================

exports.resetMasterPasswordByVerifiedPhone =
  onCall(
    {
      region: "asia-south1"
    },

    async (request) => {

      // -------------------------------------------------
      // 1. Firebase Phone Authentication required
      // -------------------------------------------------

      if (!request.auth) {

        throw new HttpsError(
          "unauthenticated",
          "Phone verification is required."
        );
      }


      // -------------------------------------------------
      // 2. Get verified phone number
      // -------------------------------------------------

      const verifiedPhone =
        String(
          request.auth.token.phone_number || ""
        ).trim();


      if (!verifiedPhone) {

        throw new HttpsError(
          "failed-precondition",
          "Verified phone number was not found."
        );
      }


      // -------------------------------------------------
      // 3. Get new password
      // -------------------------------------------------

      const newPassword =
        String(
          request.data?.newPassword || ""
        ).trim();


      if (!newPassword) {

        throw new HttpsError(
          "invalid-argument",
          "New password is required."
        );
      }


      if (newPassword.length < 8) {

        throw new HttpsError(
          "invalid-argument",
          "Password must contain at least 8 characters."
        );
      }


      if (newPassword.length > 128) {

        throw new HttpsError(
          "invalid-argument",
          "Password is too long."
        );
      }


      // -------------------------------------------------
      // 4. Convert +91XXXXXXXXXX
      //    to XXXXXXXXXX
      // -------------------------------------------------

      let mobile =
        verifiedPhone.replace(/\D/g, "");


      if (
        mobile.length === 12 &&
        mobile.startsWith("91")
      ) {
        mobile =
          mobile.substring(2);
      }


      if (mobile.length !== 10) {

        throw new HttpsError(
          "invalid-argument",
          "Invalid registered mobile number."
        );
      }


      // -------------------------------------------------
      // 5. Find Master account
      //    using registered mobile
      // -------------------------------------------------

      const db =
        getFirestore();


      const snapshot =
        await db
          .collection("masters")
          .where(
            "mobile",
            "==",
            mobile
          )
          .limit(2)
          .get();


      if (snapshot.empty) {

        throw new HttpsError(
          "not-found",
          "No Master account is registered with this mobile number."
        );
      }


      if (snapshot.size > 1) {

        throw new HttpsError(
          "failed-precondition",
          "More than one Master account uses this mobile number."
        );
      }


      const masterDoc =
        snapshot.docs[0];


      const masterUid =
        masterDoc.id;


      // -------------------------------------------------
      // 6. Verify Firebase Auth account
      // -------------------------------------------------

      let masterUser;


      try {

        masterUser =
          await getAuth()
            .getUser(
              masterUid
            );

      } catch (error) {

        logger.error(
          "Master Firebase Auth user not found",
          {
            masterUid,
            error:
              error.message
          }
        );


        throw new HttpsError(
          "not-found",
          "Master Firebase account was not found."
        );
      }


      // -------------------------------------------------
      // 7. Update ONLY password
      // -------------------------------------------------

      try {

        await getAuth()
          .updateUser(
            masterUser.uid,
            {
              password:
                newPassword
            }
          );

      } catch (error) {

        logger.error(
          "Master password update failed",
          {
            masterUid,
            error:
              error.message
          }
        );


        throw new HttpsError(
          "internal",
          "Password update failed."
        );
      }


      // -------------------------------------------------
      // 8. Do not modify:
      //
      // email
      // mobile
      // subscription
      // permissions
      // account status
      // device/session data
      // -------------------------------------------------

      logger.info(
        "Master password reset successfully",
        {
          masterUid
        }
      );


      return {
        success:
          true,

        message:
          "Password reset successfully."
      };
    }
  );

// =====================================================
// LOGIN ID RECOVERY
// The caller must first complete Firebase Phone Auth.  We never expose an
// account identifier for an unverified phone number.
// =====================================================

exports.recoverLoginIdByVerifiedPhone =
  onCall(
    { region: "asia-south1" },
    async (request) => {
      if (!request.auth) {
        throw new HttpsError("unauthenticated", "Phone verification is required.");
      }

      const phone = String(request.auth.token.phone_number || "").trim();
      if (!phone) {
        throw new HttpsError("failed-precondition", "Verified phone number was not found.");
      }

      const digits = phone.replace(/\D/g, "");
      const mobile = digits.length === 12 && digits.startsWith("91")
        ? digits.substring(2)
        : digits;
      if (mobile.length !== 10) {
        throw new HttpsError("invalid-argument", "Invalid registered mobile number.");
      }

      const db = getFirestore();
      const masterAccounts = await db
        .collection("masters")
        .where("mobile", "==", mobile)
        .limit(2)
        .get();

      if (masterAccounts.size === 1) {
        const user = await getAuth().getUser(masterAccounts.docs[0].id);
        if (!user.email) {
          throw new HttpsError("not-found", "Master login ID was not found.");
        }
        return { accountType: "MASTER", loginId: user.email };
      }
      if (masterAccounts.size > 1) {
        throw new HttpsError("failed-precondition", "More than one account uses this mobile number.");
      }

      // Employee phones are stored in E.164 form in their cloud profile.
      const employeeAccounts = await db
        .collectionGroup("employees")
        .where("mobileE164", "==", `+91${mobile}`)
        .limit(2)
        .get();
      if (employeeAccounts.size === 1) {
        const loginId = String(employeeAccounts.docs[0].get("userId") || "").trim();
        if (!loginId) {
          throw new HttpsError("not-found", "Employee login ID was not found.");
        }
        return { accountType: "EMPLOYEE", loginId };
      }
      if (employeeAccounts.size > 1) {
        throw new HttpsError("failed-precondition", "More than one account uses this mobile number.");
      }

      throw new HttpsError("not-found", "No Lakshya account is registered with this mobile number.");
    }
  );
