"""
Bổ sung trường của bảng Users theo thiết kế (fullName, avatarUrl, role, status, fcmTokens,
createdAt, lastLoginAt) cho tài liệu users/{uid} hiện có. KHÔNG xóa trường cũ
(displayName, photoUrl, firstName, lastName, lastUpdated) vì app/admin web còn đọc.

    python backfill_users.py --key <service_account.json> [--dry-run]
"""
import argparse, sys, datetime

def main():
    sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser(); ap.add_argument("--key", required=True); ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()
    import firebase_admin
    from firebase_admin import credentials, firestore, auth
    firebase_admin.initialize_app(credentials.Certificate(a.key))
    db = firestore.client()
    for snap in db.collection("users").stream():
        d = snap.to_dict() or {}
        try:
            u = auth.get_user(snap.id)
            created = datetime.datetime.fromtimestamp(u.user_metadata.creation_timestamp / 1000, datetime.timezone.utc)
            last = u.user_metadata.last_sign_in_timestamp
            last = datetime.datetime.fromtimestamp(last / 1000, datetime.timezone.utc) if last else None
        except Exception:
            u, created, last = None, None, None
        upd = {}
        if "fullName" not in d: upd["fullName"] = d.get("displayName") or (u.display_name if u else "") or ""
        if "avatarUrl" not in d: upd["avatarUrl"] = d.get("photoUrl") or (u.photo_url if u else "") or ""
        if "role" not in d: upd["role"] = "user"
        if "status" not in d: upd["status"] = "active"
        if "fcmTokens" not in d: upd["fcmTokens"] = []
        if "createdAt" not in d and created: upd["createdAt"] = created
        if "lastLoginAt" not in d and last: upd["lastLoginAt"] = last
        print(snap.id, sorted(upd))
        if upd and not a.dry_run:
            snap.reference.update(upd)

if __name__ == "__main__":
    main()
