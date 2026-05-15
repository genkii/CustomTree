# Custom Tree Mod – Bug List & Fix Tracker

> Legend: 🔴 Critical · 🟠 High · 🟡 Medium · 🔵 Low · ℹ️ Info/Design
> Status: ✅ Fixed · 🔧 In Progress · ⏳ Pending

---

## BUG-01 🟠 `TreeMatchers.OAK` matches swamp oaks — wrong NBT placed in swamp biomes

**Status:** ✅ Fixed

**File:** `src/main/java/tree/modid/TreeMatchers.java`

**Root cause:**
`TreeMatchers.OAK` is defined as `BlobFoliagePlacer + OAK_LOG trunk`. The swamp oak
(`SWAMP_OAK_TREE` feature, `ignoreVines = false`) uses the exact same foliage placer and
trunk block, so it matches **both** `OAK` and `SWAMP`.

In swamp biomes the feature pool then becomes all `oak1–10` definitions **plus** all
`swampoak1–7` definitions (17 entries), meaning only ~41 % of swamp world-gen trees
received the vine-bearing swamp-oak NBT. The other ~59 % got a plain oak NBT with no
vines — incorrect visually and structurally.

**Fix:**
Add `.and(config -> config.ignoreVines)` guard to `TreeMatchers.OAK` so it only matches
regular oaks (ignoreVines = true). Swamp oaks (ignoreVines = false) then exclusively
match `TreeMatchers.SWAMP`.

---

## BUG-02 🔴 `GiveItemPayload` server handler has no permission check — security vulnerability

**Status:** ✅ Fixed

**File:** `src/main/java/tree/modid/CustomTree.java`

**Root cause:**
A serverbound network packet `GiveItemPayload` is registered that, when received, places
any item requested by the client into the player's inventory. There is **no permission
check** on the server handler, so any authenticated player (including non-ops) can send
this packet to give themselves any item in the game — effectively a free `/give` bypass
available to every player.

**Fix:**
Add `if (!context.player().hasPermissions(2)) return;` at the top of the server handler
so only operators (level ≥ 2) can trigger the packet.

---

## BUG-03 🟡 `LivingEntityMixin.blockedHit` can be stale — incorrect combat behaviour

**Status:** ✅ Fixed

**File:** `src/main/java/tree/modid/mixin/LivingEntityMixin.java`

**Root cause:**
`blockedHit` is an instance field set inside a `@WrapOperation` that wraps the
`applyItemBlocking()` call inside `hurtServer()`. If `hurtServer()` exits **before**
reaching `applyItemBlocking()` (e.g. entity is dead, already invulnerable, absorbing
damage, etc.), the `WrapOperation` never fires and `blockedHit` keeps its value from the
previous call.

If the previous call was a blocked hit (`blockedHit = true`), the subsequent call to
`hurtServerReturn` would incorrectly see a blocked hit, reset `invulnerableTime = 0` and
return `false` — making the entity appear to not take damage even when it was genuinely
hurt and not blocking.

**Fix:**
Inject a `@Inject(at = @At("HEAD"))` on `hurtServer` that resets `blockedHit = false`
unconditionally at the start of every call, ensuring no stale state from a prior invocation.

---

## BUG-04 🔵 Typo in NBT filenames `forestbrich1/2` — inconsistency

**Status:** ✅ Fixed

**Files:**
- `src/main/resources/data/custom-tree/structures/forestbrich1.nbt` → renamed `forestbirch1.nbt`
- `src/main/resources/data/custom-tree/structures/forestbrich2.nbt` → renamed `forestbirch2.nbt`
- `src/main/java/tree/modid/CustomTree.java` registrations updated to match

**Root cause:**
The files `forestbrich1.nbt` and `forestbrich2.nbt` contain a typo ("brich" instead of
"birch"). While the registrations used the same misspelling so the files were loaded
correctly, the names are inconsistent with `forestbirch3.nbt` and `forestbirch4.nbt` and
confusing to read/search.

**Fix:**
Rename both files and update the two registration call-sites in `CustomTree.java`.

---

## BUG-05 🟡 `DebugCommand.send()` broadcasts all messages to operators — noisy

**Status:** ✅ Fixed

**File:** `src/main/java/tree/modid/DebugCommand.java`

**Root cause:**
`CommandSourceStack.sendSuccess(component, broadcastToOps)` was called with
`broadcastToOps = true`. Every line of `/customtrees status` output (dozens of lines per
call) was therefore echoed to **all online operators** in chat, regardless of who ran
the command. This makes the debug command very noisy in multi-op environments.

**Fix:**
Change the `broadcastToOps` argument to `false` in the `send()` helper.

---

## BUG-06 🟡 Forced `GameRules.LOG_ADMIN_COMMANDS = false` on every server start

**Status:** ✅ Fixed

**File:** `src/main/java/tree/modid/CustomTree.java`

**Root cause:**
Inside `ServerLifecycleEvents.SERVER_STARTED`, the mod forcefully set the server gamerule
`logAdminCommands` to `false` on **every** server start. This is unrelated to tree
generation, silently overrides any server-operator choice to keep admin-command logging
enabled, and is a security concern (operators lose audit trail of admin commands).

**Fix:**
Removed the gamerule override entirely. Server operators are free to manage
`logAdminCommands` as they see fit.

---

## BUG-07 ℹ️ `saplingBlock` field and `pickBySapling()` are dead code

**Status:** ⏳ Pending (no runtime impact — design/documentation note)

**Files:**
- `src/main/java/tree/modid/CustomTreeDefinition.java` (`saplingBlock` field, `matchesSapling()`)
- `src/main/java/tree/modid/CustomTreeRegistry.java` (`findAllBySapling()`, `pickBySapling()`)

**Root cause:**
`registerTree()` accepts a `Block sapling` parameter, and the builder exposes `.sapling()`,
both documented as "sapling that, when grown, places this custom tree". In practice the
`saplingBlock` field is **never consulted at runtime**: the only active interception point
is `TreeFeatureMixin`, which matches by `worldGenMatcher` (tree configuration) and biome,
never by sapling block type.

Sapling growth (bone meal / random tick) routes through `SaplingBlock → TreeFeature.place()`,
which `TreeFeatureMixin` already catches via the `worldGenMatcher`. The `sapling()` builder
call and the `pickBySapling()` API therefore have no effect.

**Impact:**
- Passing the wrong sapling block to `registerTree()` causes no incorrect behaviour.
- `findAllBySapling()` / `pickBySapling()` are API dead code.
- The misleading documentation may cause future contributors to believe changing the
  sapling parameter changes behaviour.

**Recommended follow-up:**
Either implement a dedicated `SaplingGrowthMixin` (with `@Inject` into `SaplingBlock.advanceTree`)
that calls `pickBySapling()`, or deprecate/remove the sapling parameters to clarify the
actual behaviour. Not fixed in this session to avoid a large refactor, but tracked here
for the next pass.

---

## Summary

| ID | Severity | Description | Status |
|----|----------|-------------|--------|
| BUG-01 | 🟠 High | `OAK` matcher matches swamp oaks → wrong NBT in swamp biomes | ✅ Fixed |
| BUG-02 | 🔴 Critical | `GiveItemPayload` handler – no permission check (security) | ✅ Fixed |
| BUG-03 | 🟡 Medium | `LivingEntityMixin.blockedHit` stale state → wrong combat results | ✅ Fixed |
| BUG-04 | 🔵 Low | Typo: `forestbrich1/2.nbt` should be `forestbirch1/2.nbt` | ✅ Fixed |
| BUG-05 | 🟡 Medium | `DebugCommand.send()` broadcasts all status lines to all ops | ✅ Fixed |
| BUG-06 | 🟡 Medium | `LOG_ADMIN_COMMANDS` forced `false` on every server start | ✅ Fixed |
| BUG-07 | ℹ️ Info | `saplingBlock` / `pickBySapling()` dead code; API misleading | ⏳ Pending |
