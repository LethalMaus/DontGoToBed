# Don't Go To Bed — roadmap

The family scrapbook, written down. Last consolidated: 15 September 2026.
Ideas are not promises, release dates or claims of implemented gameplay.

## Before the first release

These are completion/verification tasks, not a reason to add new gameplay scope.

- [ ] Resolve the two recorded Kotlin/Native inventory test failures and the native test linker workaround; obtain a clean native test run.
- [ ] Finish physical iPhone/iPad/Android performance, audio and lifecycle checks.
- [ ] Verify physical Android/iOS multiplayer in both host directions, including turns, late joins, player death, backgrounding and reconnects.
- [ ] Complete store purchases and child-audience SDK/consent checks.
- [ ] Establish retention procedures; finalize privacy pages and rebuild the bundled policies.
- [ ] Complete signing, store accounts/listings and publication preparation.
- [ ] Review the narrated Android trailer; capture a separate current iOS preview if including video on Apple's listing.

Publisher assignments and operational release steps are maintained in the private handoff.

## Next gameplay candidates

| Idea | Intended direction | Current boundary |
| --- | --- | --- |
| Story levels / campaign | More places to go and a story that develops as Mammy is found | Repeatable search/nightfall/peace rounds exist; a campaign does not |
| Crafting and richer inventory | Recipes, useful combinations, additional supplies and equipment | Materials, shapes, potions, arrows and maps already exist; crafting does not |
| Character-specific weapons and specials | Make each family character play differently | Generic combat exists; distinct special abilities do not |
| More enemy behaviour | More varied movement, decisions, encounters and bosses | Zombies and skeleton archers work; other characters may only have concept art |
| More animation | Additional character poses, movement and action animations | Some walking variants exist; the complete set is unfinished |
| Movement and visual polish | Refine collisions, feedback, effects and feel across devices | Existing physics/turning must be preserved and regression-tested |
| Easier nearby multiplayer | Better host discovery and clearer joining/recovery | Host/Join, shared world baselines and acknowledged actions already exist |

## The boys' bigger ideas

Preserved from the original README and existing family/talk notes. They need design
and playtesting before being treated as committed work.

- [ ] Drumsticks and a **drum-kit special**.
- [ ] Rabbit-themed actions and the **giant cat** idea.
- [ ] A **berserker** special.
- [ ] Aliens, a beast, a witch, a dragon, the Sandman and other boss ideas.
- [ ] **Jenny quests**, shops and more character dialogue.
- [ ] New biomes, treasure-map adventures and larger story progression.
- [ ] Energy, hunger, food and richer sleep/dream transitions.
- [ ] Explore modding support after the underlying game and asset boundaries are stable.

The current dangerous bed and map do not mean the proposed boss/campaign systems
are implemented. Source availability alone is not a modding system.

## Already implemented — keep these off the “missing features” list

- [x] Android and iOS shared Kotlin/Compose game, including iPhone/iPad support.
- [x] Leo, Ian and Papa selection; world rotation; terrain breaking/placement and shapes.
- [x] Material inventory, potions, arrows, maps and the dangerous bed.
- [x] Mammy search, nightfall and safe-time round cycle.
- [x] Local Host/Join transports, shared terrain/entity baselines and action receipts.
- [x] Saved settings, solo pause and explicit multiplayer background behaviour.
- [x] Music playlists, fades, seven effects, independent volumes and audio credits.
- [x] Direction-button option, reduced motion/flashes and descriptive control labels.
- [x] Optional tip UI/integration; actual production purchase setup/testing remains above.

Implemented does not mean every device or edge case has been verified.

## How to use this roadmap

Choose a small feature with the family, define what a player should be able to do,
then add an issue with examples and acceptance checks. Link the implementation and
playtest results when it ships. Keep speculative art distinct from playable features.

No permanent-world saves, internet matchmaking or public chat are promised here;
the current design deliberately starts fresh sessions and uses nearby multiplayer.
