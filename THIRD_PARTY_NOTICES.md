## Google Material Symbols

The `ic_home_*.xml` vectors in `app/src/main/res/drawable` are adapted from
[Google Material Symbols Rounded](https://github.com/google/material-design-icons/tree/master/symbols/android)
with Fill enabled. The vectors are licensed under the
[Apache License 2.0](https://github.com/google/material-design-icons/blob/master/LICENSE).
Their theme tint was removed so the home screen can render them in white on colored backgrounds.


## HyperOS4SwipeGate (Runtime broadcast ABI reference)

The native desktop clock settings carrier references the HyperOS Runtime broadcast ABI
verified by [HyperOS4SwipeGate](https://github.com/PzHown/HyperOS4SwipeGate),
Copyright (c) 2026 PzHown, MIT License. The MIT license text is bundled in
`app/src/main/assets/licenses/HyperOS4SwipeGate-MIT.txt`. The clock Hook and
formatting profile are independently implemented for this project's target device.

## Deadliner soft-glass navigation

The maintained `com.aritxonly.deadliner` material/navigation fork is synchronized for
bottom navigation with local Deadliner revision `5499df09383e84a120e19bd0c629c6e6ffa7ef0d`.
Copyright (c) 2024 Aritx Zhou, MIT License, included in
`app/src/main/assets/licenses/Deadliner-MIT.txt`. Source paths, local integration changes,
and validation are recorded in `docs/investigations/GPU_FLOATING_NAVIGATION.md`.

The optional official material dependency closure is vendored under
`third_party/miuix-glass`, from MIUIX PR 423 commit
`ec4c96963abf206ab2f2b0253a5efca71ba28043`, Apache-2.0. Copyright headers,
upstream hashes, and license are retained. The license is also included in
`app/src/main/assets/licenses/miuix-glass-Apache-2.0.txt`.

## AndroidLiquidGlass shader design reference

The self-developed soft-glass pipeline references shader design from
[Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass),
licensed under Apache-2.0. This attribution describes a design reference;
our material recipes, sampling adapters and host integrations are maintained locally.
The Apache-2.0 license text is bundled in
`app/src/main/assets/licenses/AndroidLiquidGlass-Apache-2.0.txt`.
