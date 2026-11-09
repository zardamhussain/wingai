const { before, after, beforeEach, test } = require('node:test');
const { readFileSync } = require('node:fs');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, updateDoc, deleteDoc, getDocs, collection, serverTimestamp, Timestamp } = require('firebase/firestore');

// Only a demo project and the local emulator are permitted. No live Firebase credentials.
let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-wingai-auth',
    firestore: { host: '127.0.0.1', port: 8085, rules: readFileSync('firestore.rules', 'utf8') }
  });
});
after(async () => { if (env) await env.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); });
const alice = () => env.authenticatedContext('alice', { email: 'alice@example.com' }).firestore();
const bob = () => env.authenticatedContext('bob', { email: 'bob@example.com' }).firestore();
const profile = () => ({ displayName: 'Alice', email: 'alice@example.com', createdAt: serverTimestamp(), updatedAt: serverTimestamp() });
const createAlice = () => setDoc(doc(alice(), 'users/alice'), profile());

test('owner can create and read profile', async () => {
  await assertSucceeds(createAlice());
  await assertSucceeds(getDoc(doc(alice(), 'users/alice')));
});
test('signed-out client cannot read or write', async () => {
  await createAlice();
  const db = env.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(db, 'users/alice')));
  await assertFails(setDoc(doc(db, 'users/anon'), profile()));
});
test('another account cannot read, update or delete profile', async () => {
  await createAlice();
  const ref = doc(bob(), 'users/alice');
  await assertFails(getDoc(ref));
  await assertFails(updateDoc(ref, { displayName: 'Bob', updatedAt: serverTimestamp() }));
  await assertFails(deleteDoc(ref));
});
test('owner can update name while preserving creation time', async () => {
  await createAlice();
  await assertSucceeds(updateDoc(doc(alice(), 'users/alice'), { displayName: 'Alice B', updatedAt: serverTimestamp() }));
});
test('owner can delete profile before deleting Auth user', async () => {
  await createAlice();
  await assertSucceeds(deleteDoc(doc(alice(), 'users/alice')));
});
test('email must match the authenticated identity', async () => {
  await assertFails(setDoc(doc(alice(), 'users/alice'), { ...profile(), email: 'bob@example.com' }));
});
test('clients cannot grant themselves paid plans, roles, quota or verification', async () => {
  for (const field of ['tier', 'role', 'quota', 'emailVerified', 'deviceUserId']) {
    await assertFails(setDoc(doc(alice(), 'users/alice'), { ...profile(), [field]: 'paid' }));
  }
});
test('owner cannot tamper with createdAt or omit updatedAt', async () => {
  await createAlice();
  await assertFails(updateDoc(doc(alice(), 'users/alice'), { createdAt: Timestamp.fromMillis(0), updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(alice(), 'users/alice'), { displayName: 'No timestamp' }));
});
test('profile schema rejects oversized or wrong-type names', async () => {
  await assertFails(setDoc(doc(alice(), 'users/alice'), { ...profile(), displayName: 'x'.repeat(61) }));
  await assertFails(setDoc(doc(alice(), 'users/alice'), { ...profile(), displayName: 42 }));
});
test('no account can enumerate users', async () => {
  await createAlice();
  await assertFails(getDocs(collection(alice(), 'users')));
});
test('unrelated collections and profile subcollections remain private', async () => {
  await assertFails(setDoc(doc(alice(), 'users/alice/chats/chat1'), { text: 'Not collected' }));
  await assertFails(setDoc(doc(alice(), 'plans/alice'), { tier: 'paid' }));
});

test('owner can save the existing WingAI installation ID', async () => {
  const ref = doc(alice(), 'users/alice');
  await assertSucceeds(setDoc(ref, { ...profile(), wingaiDeviceId: 'wing_a1b2c3d4e5' }));
  const snapshot = await getDoc(ref);
  require('node:assert/strict').equal(snapshot.data().wingaiDeviceId, 'wing_a1b2c3d4e5');
});
test('existing four-field profiles can be backfilled without resetting createdAt', async () => {
  await createAlice();
  await assertSucceeds(updateDoc(doc(alice(), 'users/alice'), {
    wingaiDeviceId: 'wing_0123456789', updatedAt: serverTimestamp()
  }));
});
test('most recently synced device can update its informational ID', async () => {
  const ref = doc(alice(), 'users/alice');
  await setDoc(ref, { ...profile(), wingaiDeviceId: 'wing_a1b2c3d4e5' });
  await assertSucceeds(updateDoc(ref, { wingaiDeviceId: 'wing_0123456789', updatedAt: serverTimestamp() }));
});
test('malformed, hardware-like and non-string device IDs are rejected', async () => {
  for (const id of ['', 'alice', 'wing_short', 'wing_ABCDEF1234', 'wing_12345678901', 42, ['wing_0123456789']]) {
    await assertFails(setDoc(doc(alice(), 'users/alice'), { ...profile(), wingaiDeviceId: id }));
  }
});
test('another user cannot write or read someone elses device ID', async () => {
  await setDoc(doc(alice(), 'users/alice'), { ...profile(), wingaiDeviceId: 'wing_a1b2c3d4e5' });
  await assertFails(updateDoc(doc(bob(), 'users/alice'), { wingaiDeviceId: 'wing_0123456789', updatedAt: serverTimestamp() }));
  await assertFails(getDoc(doc(bob(), 'users/alice')));
});
test('saving a device ID cannot grant a plan or bypass timestamp rules', async () => {
  await assertFails(setDoc(doc(alice(), 'users/alice'), { ...profile(), wingaiDeviceId: 'wing_0123456789', tier: 'paid' }));
  await createAlice();
  await assertFails(updateDoc(doc(alice(), 'users/alice'), { wingaiDeviceId: 'wing_0123456789' }));
});
