# Reverse engineering notes — OKOK international 3.1.66

Target: `com.chipsea.btcontrol.en` 3.1.66, base APK SHA-256
`638c2f8d7a5ceb44cb622545d795921a0a929488b7ecf01aa59123c2373ab353`
(164,510,609 bytes), pulled from the connected Galaxy A56.

Tooling: REA `inspect_apk_signature` for artifact identity, JADX 1.5.6 for the
decompilation (REA's Android provider needs a separately supplied
`jadx-headless-mcp` JAR, which is not installed on this host), `nm` on the
shipped native libraries.

## 1. How the app finds a scale

`scaleInfoMacs` + `com.chipsea.btlib.util.BtScanUtil` /
`ScaleEncryptedKeyUtils` / `com.chipsea.btlib.protocal.chipseaBroadcastFrame`.

The scanner itself is unfiltered (`startScan(callback)`), but a result is only
accepted when the **manufacturer-specific AD record** (AD type `0xFF`) starts
with one of these bytes:

| first byte | parser | contents |
|---|---|---|
| `0xC0` | `okokBroadcasetV2Parser` | seq, weight, impedance, product id, scale property; optionally encrypted |
| `0xCA` + `0x20` | `okokBroadcastParser` | product id, device type, scale property, weight, impedance |
| `0xCA` otherwise | `okokParser` | legacy v1/v2 weight frames |
| `0xFF` | `okokCloudParser` | cloud/ali frame, weight |

The decoded MAC is then compared with a list of scales bound to the account
(`scaleInfoMacs`, fetched from the vendor backend), which is why the stock app
"somehow" only shows your own scale. V2 frames can encrypt the weight,
impedance and MAC with a per-device key obtained from the vendor server
(`OkokScaleEncryptedKeyUtils`); without that key the stock app ignores them.

**Our app inverts this:** it accepts any advertisement whose manufacturer data
starts with `0xC0`, `0xCA` or `0xFF`, so no account or MAC whitelist is needed.
Scale identity is the BLE source address. Encrypted v2 frames are still listed
(so you can connect and read over GATT) but their broadcast weight is not shown.

Files: `ble/ScaleAdvertisement.java` (detection), `ble/BleScaleClient.java`
(scan wiring).

## 2. GATT profile

From `com.chipsea.btlib.model.BtGattAttr`:

| service | notify (TX) | write (RX) |
|---|---|---|
| `0000fff0-…` Chipsea | `0000fff2-…` | `0000fff1-…` |
| `0000181b-…` Body Composition | `00002a9c-…` | — |
| `0000181d-…` Weight Scale | `00002a9d-…` | — |

After subscribing the app writes the user profile so the scale can compute body
fat itself (`syncChipseaInstruction.sycnAllRoleInfo_v11`):

```
CA 11 10 10 11 <timestamp:4> 00 00 <roleId:4> <sex|age> <heightCm> <weight*10:2> <xor>
```

`sex|age` = `(male ? 0x80 : 0) | age`; the last byte is XOR of bytes 1..18.

## 3. Measurement frames

`chipseaStraightFrame.process`:

**0x10 — weight + composition**

```
[0]=0xCA [1]=0x10 [2]=len [3]=? [4]=scaleProperty
[5..6]=weight  [7..8]=fat  [9..10]=water  [11..12]=muscle
[13..14]=BMR   [15..16]=visceral  [17]=bone
```

Composition fields are present when bit 0 of `scaleProperty` is set.

**0x11 — stream frames**, `[3]` selects the sub type:

- `0/1` – live weight (`[5..6]`, property `[11]`)
- `0x12`/`0x13` – multi-packet composition; packet byte `[4]` is
  `(index << 4) | total`, payloads concatenated, then:
  timestamp `[0..3]`, property `[4]`, role id `[6..9]`, weight `[10..11]`,
  fat `[12..13]`, water `[14..15]`, muscle `[16..17]`, BMR `[18..19]`,
  visceral `[20..21]`, bone `[22]`
- `0x14` – role-match answer

Weight decoding (`WeightUnitUtil.Parser` + `BytesUtil`):

- big-endian 16-bit, decimal digits from bits 6..5 of the property
  (`01` → none, `10` → /10, else /100)
- unit from bits 4..3 (`00` kg, `01` jin ×0.5, `10` lb ×0.4535924, `11` stone)

Percentages are tenths of a percent; bone mass is tenths of a kg.

## 4. Body-composition math

Two native libraries implement the vendor algorithm and are called through
`com.example.jnipack.Myndk` / `chipsea.bias.vA019.CSBiasAPI`:

- `libBodyFatScaleAlg.so` – single/4/8-electrode engine, exports `getBodyFatPCT`,
  `getBMR`, `getBodyAge`, `getVFAL`, `getMuscleKg`, `getBoneKg`, `getWaterICWKg`,
  `getSMI`, `getTargetWeight`, `getBodyScore`, …
- `libchipsea_bias_v235.so` – single-impedance fit (`cs_bias_v235(sex, age,
  height, weight*100, impedance*10)` → BFP, SLM, BWP, BMC, VFR, PP, SMM, SBW,
  MC, WC, FC, BMR, MA, SBC)
- `libchipsea_bias_vA019.so` – 8-electrode, 10 impedance values

The Java fallback in `com.chipsea.code.algorithm.CsAlgoBuilder` contains the
plain-text linear fits, which is what was reimplemented:

| metric | male | female |
|---|---|---|
| body fat % | `(-0.3315·H + 0.6216·W + 0.0183·A + 0.0085·Z + 22.554)/W·100` | `(-0.3332·H + 0.7509·W + 0.0196·A + 0.0072·Z + 22.7193)/W·100` |
| soft lean mass | `0.2867·H + 0.3894·W − 0.0408·A − 0.01235·Z − 15.7665` | `0.3186·H + 0.1934·W − 0.0206·A − 0.0132·Z − 16.4556` |
| total water % | `(0.0939·H + 0.3758·W − 0.0032·A − 0.006925·Z + 0.097)/W·100` | `(0.0877·H + 0.2973·W + 0.0128·A − 0.00603·Z + 0.5175)/W·100` |
| visceral fat | `−0.2675·H + 0.42·W + 0.1462·A + 0.0123·Z + 13.9871` | `−0.1651·H + 0.2628·W + 0.0649·A + 0.0024·Z + 12.3445` |
| BMR (kcal) | `7.5037·H + 13.1523·W − 4.3376·A − 0.3486·Z − 311.7751` | `7.5432·H + 9.9474·W − 3.4382·A − 0.309·Z − 288.2821` |
| body age | `−0.7471·H + 0.9161·W + 0.4184·A + 0.0517·Z + 54.2267` | `−1.1165·H + 1.5784·W + 0.4615·A + 0.0415·Z + 83.2548` |
| ideal weight | `(H − 80)·0.7` | `(H − 70)·0.6` |

`H` cm, `W` kg, `A` years, `Z` ohm. Clamps copied from the original:
fat 5–45 %, water 20–85 %, lean mass 20–70 kg, visceral 1–59, body age within
±10 years of the real age and 18–80, score 45–100.

Body score (`calScore`) mixes BMI (quadratic fit), an ideal-BMR polynomial,
muscle vs. target and visceral fat; the app reproduces this.

When the scale sends composition itself (the normal case over GATT), those
values win; the formulas above only fill gaps (calculator, weight-only frames).

## 5. What is verified / not verified

Verified statically: package identity, ad SDK inventory, BLE UUIDs, frame
layouts, outgoing profile packet, equations, clamps, UI flow.

Verified on device: build/install, scan list, advertisement filtering, calculator
and result rendering, history persistence.

Verified on hardware (17 Nov 2026, scale `B4:56:5D:38:30:42`): the scale
advertises `connectable=false`, so GATT always ends in `GATT_CONNECTION_TIMEOUT`
(reason 8). The broadcast carries everything needed:

```
C0 35 16 D0 17 70 0A 01 25 B4 56 5D 38 30 42
   │  └─┬─┘ └─┬─┘ └─┬─┘ │  └──── MAC ────┘
   │  weight  Z    product  flags (bit0 = complete)
   └ sequence
weight = 0x16D0 / 100 = 58.40 kg
Z      = 0x1770 / 10  = 600.0 Ω
```

A full weighing produced 58.4 kg, 11.1 % fat, 45.7 % muscle (26.7 kg), 58.6 %
water, visceral 3.5, on the default 175 cm / 30 / male profile, so the derived
equations and the frame decoding are confirmed end-to-end.

Note on resolution bits: `getDigit` (used by broadcasts) reads bits 2..1 of the
scale-property byte (`01` → whole units, `10` → 0.01, else 0.1); the GATT parser
uses a different helper, which is why both are implemented separately.

Still unverified: an 8-electrode/segmental scale (arms/legs/trunk values) and
the v2 frames whose weight/impedance are XOR-encrypted with a server-issued key
(those are detected and listed, but their broadcast values are not decoded).
