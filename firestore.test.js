const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_1_UID = "admin_slot_1";
const ADMIN_2_UID = "admin_slot_2";
const THIRD_USER_UID = "third_user_blocked";
const CUSTOMER_1_UID = "customer_alice";
const CUSTOMER_2_UID = "customer_bob";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

async function claimAdminSlot(uid, slotNumber, existingUids = []) {
  const db = testEnv.authenticatedContext(uid).firestore();
  const batch = db.batch();
  const registryRef = db.collection("config").doc("adminRegistry");
  const adminRef = db.collection("admins").doc(uid);
  const now = new Date(Date.now() - 1000);

  if (existingUids.length === 0) {
    batch.set(registryRef, {
      adminUids: [uid],
      maxAdmins: 2,
      updatedAt: now,
    });
  } else {
    batch.update(registryRef, {
      adminUids: [...existingUids, uid],
      maxAdmins: 2,
      updatedAt: now,
    });
  }

  batch.set(adminRef, {
    uid,
    email: `${uid}@kjirons.com`,
    displayName: `Admin ${slotNumber}`,
    slotNumber,
    createdAt: now,
  });

  return batch.commit();
}

async function createCustomer(uid, name = "Alice") {
  const db = testEnv.authenticatedContext(uid).firestore();
  const now = new Date(Date.now() - 1000);
  return db.collection("users").doc(uid).set({
    uid,
    role: "customer",
    name,
    email: `${uid}@gmail.com`,
    mobile: "9876543210",
    address: "42 Green Park Lane",
    locationLabel: "Green Park",
    latitude: 12.9716,
    longitude: 77.5946,
    createdAt: now,
    updatedAt: now,
  });
}

function buildPendingOrder(customerId, override = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    customerId,
    customerName: "Alice",
    customerMobile: "9876543210",
    serviceType: "Steam Ironing",
    fabricType: "Starched Shirt",
    dressType: "Shirt / Kurta",
    quantity: 3,
    roseWaterAdded: true,
    extraStarchAdded: false,
    deliveryMethod: "doorstep_pickup_delivery",
    pickupAddress: "42 Green Park Lane",
    locationLabel: "Green Park",
    latitude: 12.9716,
    longitude: 77.5946,
    notes: "Crisp collar please",
    status: "pending",
    rejectionReason: "",
    unitPrice: 45,
    addOnPrice: 20,
    deliveryFee: 25,
    totalPrice: 180,
    createdAt: now,
    updatedAt: now,
    ...override,
  };
}

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("config").doc("adminRegistry").get());
  await assertFails(unauthDb.collection("catalog").doc("pricing").get());
  await assertFails(unauthDb.collection("orders").get());
  await assertFails(unauthDb.collection("users").doc(CUSTOMER_1_UID).get());
});

test("First two admins can claim slots atomically; third admin is rejected", async () => {
  await assertSucceeds(claimAdminSlot(ADMIN_1_UID, 1, []));
  await assertSucceeds(claimAdminSlot(ADMIN_2_UID, 2, [ADMIN_1_UID]));

  // Third account trying to claim Admin slot must fail
  await assertFails(claimAdminSlot(THIRD_USER_UID, 2, [ADMIN_1_UID, ADMIN_2_UID]));

  // Direct write to admins/{uid} without updating adminRegistry must fail
  const thirdDb = testEnv.authenticatedContext(THIRD_USER_UID).firestore();
  await assertFails(
    thirdDb.collection("admins").doc(THIRD_USER_UID).set({
      uid: THIRD_USER_UID,
      email: "rogue@kjirons.com",
      displayName: "Rogue",
      slotNumber: 2,
      createdAt: new Date(Date.now() - 1000),
    })
  );
});

test("Strict role separation: customer cannot become admin and admin cannot create customer profile or order", async () => {
  await assertSucceeds(claimAdminSlot(ADMIN_1_UID, 1, []));
  await assertSucceeds(createCustomer(CUSTOMER_1_UID, "Alice"));

  // Customer cannot claim slot 2 admin
  await assertFails(claimAdminSlot(CUSTOMER_1_UID, 2, [ADMIN_1_UID]));

  // Admin cannot create a customer profile on the same account
  const adminDb = testEnv.authenticatedContext(ADMIN_1_UID).firestore();
  const now = new Date(Date.now() - 1000);
  await assertFails(
    adminDb.collection("users").doc(ADMIN_1_UID).set({
      uid: ADMIN_1_UID,
      role: "customer",
      name: "Admin As Customer",
      email: "admin@kjirons.com",
      mobile: "9999999999",
      address: "1 Admin St",
      locationLabel: "HQ",
      latitude: 12.97,
      longitude: 77.59,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Admin cannot place customer orders
  await assertFails(
    adminDb.collection("orders").doc("order_admin_blocked").set(buildPendingOrder(ADMIN_1_UID))
  );

  // Customer cannot escalate role field on update
  const custDb = testEnv.authenticatedContext(CUSTOMER_1_UID).firestore();
  await assertFails(
    custDb.collection("users").doc(CUSTOMER_1_UID).update({
      role: "admin",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Only Admin can update catalog pricing; customers can read it", async () => {
  await assertSucceeds(claimAdminSlot(ADMIN_1_UID, 1, []));
  await assertSucceeds(createCustomer(CUSTOMER_1_UID, "Alice"));

  const adminDb = testEnv.authenticatedContext(ADMIN_1_UID).firestore();
  const custDb = testEnv.authenticatedContext(CUSTOMER_1_UID).firestore();
  const now = new Date(Date.now() - 1000);
  const catalogPayload = {
    servicePrices: { "Wash & Fold": 40, "Dry Cleaning": 120, "Steam Ironing": 25 },
    fabricPrices: { Cotton: 0, "Starched Shirt": 20, Sarees: 60 },
    dressTypePrices: { "Shirt / Kurta": 10, Saree: 30 },
    roseWaterFee: 15,
    extraStarchFee: 20,
    doorstepDeliveryFee: 25,
    updatedBy: ADMIN_1_UID,
    updatedAt: now,
  };

  await assertFails(custDb.collection("catalog").doc("pricing").set(catalogPayload));
  await assertSucceeds(adminDb.collection("catalog").doc("pricing").set(catalogPayload));
  await assertSucceeds(custDb.collection("catalog").doc("pricing").get());
});

test("Customer order lifecycle: customer creates pending & sees only own orders; central admin dashboard sees all and transitions cleanly", async () => {
  await assertSucceeds(claimAdminSlot(ADMIN_1_UID, 1, []));
  await assertSucceeds(createCustomer(CUSTOMER_1_UID, "Alice"));
  await assertSucceeds(createCustomer(CUSTOMER_2_UID, "Bob"));

  const aliceDb = testEnv.authenticatedContext(CUSTOMER_1_UID).firestore();
  const bobDb = testEnv.authenticatedContext(CUSTOMER_2_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_1_UID).firestore();

  // Customer cannot create an order directly in 'accepted' or 'ready' state
  await assertFails(
    aliceDb
      .collection("orders")
      .doc("order_skip")
      .set(buildPendingOrder(CUSTOMER_1_UID, { status: "accepted" }))
  );

  // Alice and Bob create pending orders
  await assertSucceeds(
    aliceDb.collection("orders").doc("order_alice_1").set(buildPendingOrder(CUSTOMER_1_UID))
  );
  await assertSucceeds(
    bobDb
      .collection("orders")
      .doc("order_bob_1")
      .set(buildPendingOrder(CUSTOMER_2_UID, { customerName: "Bob", fabricType: "Sarees" }))
  );

  // Customer filtered query succeeds; unfiltered query fails; cross-user read fails
  const aliceOrders = await assertSucceeds(
    aliceDb.collection("orders").where("customerId", "==", CUSTOMER_1_UID).get()
  );
  assert.equal(aliceOrders.size, 1);
  await assertFails(aliceDb.collection("orders").get());
  await assertFails(aliceDb.collection("orders").doc("order_bob_1").get());

  // Customer cannot change own order status
  await assertFails(
    aliceDb.collection("orders").doc("order_alice_1").update({
      status: "accepted",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Central Admin dashboard sees ALL orders
  const allAdminOrders = await assertSucceeds(adminDb.collection("orders").get());
  assert.equal(allAdminOrders.size, 2);

  // Admin cannot skip steps (pending -> ready directly must fail)
  await assertFails(
    adminDb.collection("orders").doc("order_alice_1").update({
      status: "ready",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Valid pipeline: pending -> accepted -> processing -> ready -> out_for_delivery -> delivered
  for (const nextStatus of ["accepted", "processing", "ready", "out_for_delivery", "delivered"]) {
    await assertSucceeds(
      adminDb.collection("orders").doc("order_alice_1").update({
        status: nextStatus,
        updatedAt: new Date(Date.now() - 200),
      })
    );
  }

  // Delivered terminal state cannot be mutated further
  await assertFails(
    adminDb.collection("orders").doc("order_alice_1").update({
      status: "processing",
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Admin rejects Bob's order with reason; rejected terminal state locks
  await assertSucceeds(
    adminDb.collection("orders").doc("order_bob_1").update({
      status: "rejected",
      rejectionReason: "Outside pickup hours",
      updatedAt: new Date(Date.now() - 150),
    })
  );
  await assertFails(
    adminDb.collection("orders").doc("order_bob_1").update({
      status: "accepted",
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Order-linked chat isolates messages between owning customer and admin", async () => {
  await assertSucceeds(claimAdminSlot(ADMIN_1_UID, 1, []));
  await assertSucceeds(createCustomer(CUSTOMER_1_UID, "Alice"));
  await assertSucceeds(createCustomer(CUSTOMER_2_UID, "Bob"));

  const aliceDb = testEnv.authenticatedContext(CUSTOMER_1_UID).firestore();
  const bobDb = testEnv.authenticatedContext(CUSTOMER_2_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_1_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("orders").doc("order_chat_1").set(buildPendingOrder(CUSTOMER_1_UID))
  );

  const now = new Date(Date.now() - 500);
  // Alice sends customer message
  await assertSucceeds(
    aliceDb.collection("orders").doc("order_chat_1").collection("messages").doc("msg_1").set({
      orderId: "order_chat_1",
      customerId: CUSTOMER_1_UID,
      senderId: CUSTOMER_1_UID,
      senderName: "Alice",
      senderRole: "customer",
      text: "Please use extra rose water!",
      createdAt: now,
    })
  );

  // Admin replies on the same order
  await assertSucceeds(
    adminDb.collection("orders").doc("order_chat_1").collection("messages").doc("msg_2").set({
      orderId: "order_chat_1",
      customerId: CUSTOMER_1_UID,
      senderId: ADMIN_1_UID,
      senderName: "KJ Iron's Admin",
      senderRole: "admin",
      text: "Sure, we have added rose water rinse.",
      createdAt: now,
    })
  );

  // Alice and Admin can read messages; Bob cannot
  await assertSucceeds(
    aliceDb
      .collection("orders")
      .doc("order_chat_1")
      .collection("messages")
      .where("customerId", "==", CUSTOMER_1_UID)
      .get()
  );
  await assertSucceeds(
    adminDb.collection("orders").doc("order_chat_1").collection("messages").get()
  );
  // Unfiltered query on Alice's order chat fails for Bob
  await assertFails(
    bobDb
      .collection("orders")
      .doc("order_chat_1")
      .collection("messages")
      .get()
  );
  // Attempting to filter by Alice's customerId fails for Bob
  await assertFails(
    bobDb
      .collection("orders")
      .doc("order_chat_1")
      .collection("messages")
      .where("customerId", "==", CUSTOMER_1_UID)
      .get()
  );
  // Direct read and write into Alice's order chat fail for Bob
  await assertFails(
    bobDb.collection("orders").doc("order_chat_1").collection("messages").doc("msg_1").get()
  );
  await assertFails(
    bobDb.collection("orders").doc("order_chat_1").collection("messages").doc("msg_bob").set({
      orderId: "order_chat_1",
      customerId: CUSTOMER_2_UID,
      senderId: CUSTOMER_2_UID,
      senderName: "Bob",
      senderRole: "customer",
      text: "Hijack chat",
      createdAt: now,
    })
  );
});

test("Customer can edit profile and add/update bounded photoBase64 DP; oversized DP and cross-user edits fail", async () => {
  await assertSucceeds(createCustomer(CUSTOMER_1_UID, "Alice"));
  const aliceDb = testEnv.authenticatedContext(CUSTOMER_1_UID).firestore();
  const bobDb = testEnv.authenticatedContext(CUSTOMER_2_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(CUSTOMER_1_UID).update({
      name: "Alice Updated",
      photoBase64: "data_base64_sample_avatar",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Oversized photoBase64 (> 80000 chars) must fail
  await assertFails(
    aliceDb.collection("users").doc(CUSTOMER_1_UID).update({
      photoBase64: "a".repeat(80001),
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Bob cannot edit Alice's profile
  await assertFails(
    bobDb.collection("users").doc(CUSTOMER_1_UID).update({
      name: "Hijacked",
      updatedAt: new Date(Date.now() - 400),
    })
  );
});

