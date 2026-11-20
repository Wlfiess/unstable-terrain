# Unstable Terrain 1.0 Paper verification

Both release JARs were tested on Paper 1.21.11 build 132 with Java 21. The test worlds used seed `-8647155992503787980`. They were created separately from player saves. Both release JARs still identify as version `1.0`.

## Terrain, structures, and commands

| Check | Result |
| --- | --- |
| Farlands generation | Sampled 74,652 generated block states across all 17 regions in a fresh Paper world. |
| Spokelands generation | Sampled all nine regions on the north and west faces. Verified 23,040 column states, 18 former landmark locations, and tree clearance over 8,348 Sculk river columns. |
| Great Sea, Amplified, and Underworld | Verified the region landings, Underworld shelf generation and extended Nether bounds, Great Sea transitions at four inner shores and the outer shore, and ordinary ocean rules for gravel and ice. |
| Standard structures | The Lakerek Temple, Zam Empire, and Orbital array generated at their intended sites. The sampled south mirrors had no duplicate landmark. All 403 temple block entities passed the native check. |
| No-landmark edition | Compared 153 full chunks across the same 17 sites in each edition. The temple, empire, and Orbitals were absent in this edition. Bridge houses, repeating dungeons, three sample trial chambers, a mineshaft, shipwreck, ocean ruin, and monument still generated. |
| Teleports and permissions | Ran all 17 named Farlands teleports, all 18 named Spokelands north/west teleports, all three Great Sea destinations, `/amplified`, `/underworld`, and `/mist`. All seven plugin command roots and the Paper namespace aliases required OP. A connected non-op had 22 command packets rejected, and access was revoked again after de-op. |
| Gameplay rules | Verified live survival-to-adventure switching in the Underworld, restoration on exit and when the setting is disabled, separate mainland and Farlands Ender Chest contents, Farlands fireworks restrictions, default mob suppression, Dark Forest blindness, and the Orbital portal restriction. |
| Mist | Both builds generated snowy taiga and frozen river at the center. A connected client received snowfall and dense fog outdoors in the no-landmark edition. The fog stayed in a natural cave, cleared under a timber roof and inside a generated trial chamber, and returned outdoors. |

## Orbitals

- Generated all 100 pressure-plate stations at 100-block spacing. Checked 600 positions along the track and across its joins against the source pattern. Verified one signed control platform, one golem, and one Breeze riding its minecart.
- Verified continuous 30-second arming, station lockout with the lever off, faster Breeze fire, smooth accelerated daylight, and the return to ordinary daylight timing when the control chunk unloads. Breaking a station block prevented that station from loading again after rearming.
- Used both the operator test pull and a real ender pearl. A linked rod waited while its station chunk was forced to tick, then charged after it unloaded.
- Fired both shot types on the server. Stab consumed its rod, started after a short delay, cleared stone above and below the player, and preserved obsidian and bedrock. Nuke consumed its rod and expanded from one primed TNT to 661 with matching fuses. The server confirmed they had all detonated.
- Let an armed charge expire after ten minutes. The same healthy station rejected a new rod after expiry and accepted one after a fresh 30-second charge.
- Pressed the button on the pasted standalone pearl launcher schematic. The player landed at X 12,550,824.5, just inside the Farlands entrance.

## Verification notes

All 10 Paper unit tests passed. The older opt-in integration verifier had stale fixture assertions and a mock player incompatible with the current Paper hook. The expanded integration runs used corrected test classes in disposable copies of the JAR. Runtime classes and release files stayed unchanged. These results cover the sampled seed and configurations above.

| Release JAR | SHA-256 |
| --- | --- |
| Standard | `2C09C32097154C48A4899171DA59CF99F06CAC4859FF7EA873C7AC16B6B75C5D` |
| No landmark structures | `2DD901BABA6478BE0EE37ED077A4CC0CEA63A94D1866FA0D6EDCD6BCBBB09061` |
