const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const { defineSecret } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");

const githubToken = defineSecret("MY_INDIA_TV_GITHUB_TOKEN");

exports.syncChannelTxtOnChange = onDocumentWritten(
  {
    document: "channels/{channelId}",
    region: "asia-south1",
    secrets: [githubToken],
    retry: true,
  },
  async (event) => {
    const token = githubToken.value();
    if (!token) {
      throw new Error("MY_INDIA_TV_GITHUB_TOKEN secret is not configured.");
    }

    const response = await fetch(
      "https://api.github.com/repos/vinod-rajput-vg/My-India-TV/dispatches",
      {
        method: "POST",
        headers: {
          Accept: "application/vnd.github+json",
          Authorization: `Bearer ${token}`,
          "X-GitHub-Api-Version": "2022-11-28",
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ event_type: "sync-channel-txt" }),
      }
    );

    if (response.status !== 204) {
      const details = await response.text();
      logger.error("GitHub repository_dispatch failed", {
        status: response.status,
        details,
      });
      throw new Error(`GitHub dispatch failed with HTTP ${response.status}`);
    }

    logger.info("Requested TXT sync after Firestore channel change", {
      channelId: event.params.channelId,
      changeType: event.data?.before?.exists
        ? (event.data?.after?.exists ? "updated" : "deleted")
        : "created",
    });
  }
);
