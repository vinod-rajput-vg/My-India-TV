# Automatic Firestore-to-TXT sync

The Firestore trigger in `index.js` dispatches the GitHub Actions workflow whenever a document in the `channels` collection is created, updated, or deleted. The workflow regenerates the eight category TXT files from Firestore. The existing five-minute schedule remains as a fallback.

## One-time setup (requires project owner/admin access)

1. Install Node.js 20 and the Firebase CLI on a trusted computer.
2. From the repository root, run:

   ```sh
   npm --prefix functions install
   firebase login
   firebase use my-india-tv-b06da
   ```

3. Create a GitHub fine-grained personal access token for **only** the `vinod-rajput-vg/My-India-TV` repository. Grant **Contents: Read and write** permission. Do not put the token in browser code, commit it, or share it in chat.
4. Store the token as a Firebase Functions secret (the CLI prompts for its value):

   ```sh
   firebase functions:secrets:set MY_INDIA_TV_GITHUB_TOKEN
   ```

5. Deploy the trigger:

   ```sh
   firebase deploy --only functions:syncChannelTxtOnChange
   ```

6. Add or update a test channel in the Admin panel, then open GitHub Actions and confirm a new **Sync channel order to TXT** run starts automatically. Also test a sort and delete. The five-minute schedule is retained as a backup.

## Notes

- Cloud Functions deployment may require the Firebase project to use the Blaze billing plan and have the required Google Cloud APIs enabled.
- The service account secret `FIREBASE_SERVICE_ACCOUNT_JSON` must still exist in GitHub Actions for the workflow to read Firestore.
- If deployment fails, check the Firebase CLI output. Do not paste credentials or token values into issue reports.
