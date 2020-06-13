// Create a user for API server
db.createUser({
  user: "api",
  pwd: "abc",
  roles: [{ role: "readWrite", db: "habanero" }],
});

// == Accounts ===========================================================================
db.createCollection("accounts", {
  validator: {
    $jsonSchema: {
      required: [
        "id",
        "email",
        "screenName",
        "passwordDigest",
        "isCertificated",
        "signedUpAt"
      ],
    },
  },
});
db.accounts.ensureIndex({ id: 1 }, { unique: true });
db.accounts.ensureIndex({ email: 1 }, { unique: true });

// == Email Approval =====================================================================
db.createCollection("certificationTokens", {
  validator: {
    $jsonSchema: { required: ["accountId", "token", "expireAt"] },
  },
});
db.certificationTokens.ensureIndex({ accountId: 1 }, { unique: true });
db.certificationTokens.ensureIndex({ token: 1 }, { unique: true });

// == Refresh Tokens =====================================================================
db.createCollection("refreshTokens", {
  validator: { $jsonSchema: { required: ["accountId", "token", "expireAt"] } },
});
db.refreshTokens.ensureIndex({ token: 1 }, { unique: true });
