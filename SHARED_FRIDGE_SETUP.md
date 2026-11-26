# Private household preview

1. In Firebase Authentication create the two email/password users.
2. In Cloud Firestore create document `households/home` with an array field `memberUids` containing the exact two Authentication UIDs (strings).
3. Publish the contents of `firestore.rules` in the Firebase console. Do not use test-mode rules.
4. Install the test APK on both phones, sign in under Settings > Shared fridge (preview), and add a disposable food record on one phone. Check the other phone, quantity changes, edits and removal.
5. Test that an account not listed in memberUids cannot see or change inventory.

The preview uses a separate shared collection and requires a server connection for writes. No Room migration or import occurs. Photos, local notifications, category presets, CSV import/export and offline writes are not shared. Test APK signatures may differ between builds. Do not use production inventory until these limitations are addressed. Membership changes must be made in the Firebase console; the app cannot change membership. The rules file is a reference and is not automatically deployed by the APK workflow.
