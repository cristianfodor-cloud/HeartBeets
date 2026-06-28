# HeartBeets Blockchain Integration Plan

## Overview

HeartBeets heartbeats are minted as NFTs on Solana. Users burn DOGP tokens to create NFTs, which can be traded on Solana marketplaces.

---

## Ecosystem

```
         ┌─────────────────┐
         │   DogPlanet     │  ← ONLY SOURCE of DOGP
         │  (help dogs)    │
         └────────┬────────┘
                  │ earn / buy (dogplanet.app)
                  ▼
              ┌──────┐
              │ DOGP │  (no DEX liquidity, not on Raydium)
              └──┬───┘
                 │ burn
        ┌────────┼────────┐
        ▼        ▼        ▼
   HeartBeets  VibeIn   Future Apps
   (burn→NFT) (subscription)
```

- **DOGP is only obtainable** via DogPlanet app (earn by helping stray dogs) or buying at dogplanet.app (send SOL, receive DOGP)
- **No DEX trading** — removed from Raydium, no speculative trading
- **Utility token** — spent/burned across the app ecosystem

---

## Token Flow

| Action | DOGP Effect |
|--------|-------------|
| Earn in DogPlanet | +DOGP to user wallet |
| Buy at dogplanet.app | SOL → treasury, DOGP → user wallet |
| Mint heartbeat NFT | DOGP burned (permanent supply reduction) |
| VibeIn subscription | DOGP burned or sent to treasury |

---

## Heartbeat NFT Model

### Minting
- User chooses how much DOGP to burn (minimum enforced, no maximum)
- DOGP is **burned** (sent to dead address) — not locked, not recoverable
- cNFT (compressed NFT) minted to user's Solana wallet
- Heartbeat config + voice recording uploaded to Arweave (permanent storage)
- NFT metadata includes "DOGP Burned: X" as a trait

### Ownership & Listening
- Only the NFT holder can listen to the heartbeat via HeartBeets app
- If creator transfers/sells the NFT, they lose access
- App verifies NFT ownership via Solana RPC (`getAssetsByOwner`)

### Trading
- NFTs are tradeable on Solana marketplaces (Tensor, Magic Eden) for SOL
- Higher DOGP burned = potentially higher perceived value
- Per-creator NFT collections

### Free Tier
- First 3 NFTs per user: free (treasury pays SOL fees, no DOGP burn required)
- After 3: user must burn DOGP to mint

---

## NFT Metadata Structure

```json
{
  "name": "Heartbeat by [Creator Name]",
  "symbol": "HBEET",
  "description": "A heartbeat created on HeartBeets",
  "image": "[arweave URI - generated artwork or default]",
  "attributes": [
    { "trait_type": "Creator", "value": "Display Name" },
    { "trait_type": "DOGP Burned", "value": "100" },
    { "trait_type": "Duration", "value": "2m 13s" },
    { "trait_type": "BPM Range", "value": "60-80" },
    { "trait_type": "Sound Preset", "value": "Warm" },
    { "trait_type": "Has Voice Message", "value": "Yes" }
  ],
  "properties": {
    "files": [
      { "uri": "[arweave URI]", "type": "application/json" }
    ]
  }
}
```

---

## Authentication & Wallet

- **Google Sign-In** required on HeartBeets (same as DogPlanet and VibeIn)
- **Phantom wallet** connected via Mobile Wallet Adapter
- **Shared wallet** across all apps — linked to Google account in Firestore:

```
Firestore: /users/{googleUid}
  - displayName: string
  - email: string (private, not on-chain)
  - photoUrl: string
  - solanaWallet: string (from Phantom connection)
```

---

## User Flows

### Create & Mint
1. User signs in with Google on HeartBeets
2. User connects Phantom wallet (Mobile Wallet Adapter)
3. User creates heartbeat (sound + voice + rhythm)
4. User taps "Mint NFT" → chooses DOGP amount
5. Phantom opens → user approves burn + mint transaction
6. Cloud Function uploads heartbeat data to Arweave
7. cNFT minted to user's wallet
8. NFT appears in "My Heartbeats" (owned NFTs)

### Listen
1. User opens HeartBeets → signed in + Phantom connected
2. App queries Solana: `getAssetsByOwner(wallet)`
3. Shows all heartbeat NFTs user owns (created + received/bought)
4. Tap NFT → fetch heartbeat config from Arweave → play

### Send/Trade
1. User sends NFT to any Solana wallet (via Phantom or marketplace)
2. Sender loses access to listen
3. Recipient can listen in HeartBeets app

---

## Technical Architecture

### Android App (HeartBeets)
- Google Sign-In (Firebase Auth)
- Phantom Mobile Wallet Adapter (Solana transaction signing)
- Arweave fetching (download heartbeat config + voice for playback)
- Solana RPC calls (check NFT ownership)

### Firebase Cloud Function
- Triggered after successful on-chain mint
- Uploads heartbeat config JSON + voice recording to Arweave
- Returns Arweave URIs for NFT metadata

### Solana Program (Anchor/Rust)
- `mint_heartbeat(dogp_amount: u64, metadata_uri: string)`
  - Burn X DOGP from user's token account
  - Mint cNFT to user via Metaplex Bubblegum
  - Store DOGP burned amount in metadata
- Uses Metaplex cNFT standard (cheap minting ~$0.001 per NFT)

### Storage
- **Arweave**: heartbeat config JSON + voice recordings (permanent, decentralized)
- **Firestore**: user accounts, wallet mapping, free tier tracking
- **On-chain**: NFT ownership, DOGP burned amount, metadata URI

---

## Implementation Order

| Step | Task | Location |
|------|------|----------|
| 1 | Google Sign-In | HeartBeets app |
| 2 | Phantom Mobile Wallet Adapter | HeartBeets app |
| 3 | Solana program (burn DOGP + mint cNFT) | Anchor/Rust |
| 4 | Arweave upload Cloud Function | Firebase |
| 5 | Mint flow in app (choose amount → Phantom → confirm) | HeartBeets app |
| 6 | NFT ownership check + listen from Arweave | HeartBeets app |
| 7 | Replace Firebase sharing with NFT-based system | HeartBeets app |
| 8 | Free tier logic (first 3 free) | Cloud Function |

---

## Key Decisions Made

- [x] Burn model (not vault/lock) — DOGP is permanently destroyed
- [x] No DEX liquidity — DOGP only from DogPlanet
- [x] Variable burn amount (user chooses, minimum enforced)
- [x] cNFTs for cheap minting
- [x] Arweave for permanent storage
- [x] Google Sign-In required
- [x] Phantom Mobile Wallet Adapter for signing
- [x] Per-creator NFT collections
- [x] Creator loses access after transfer (true ownership)
- [x] First 3 NFTs free, then DOGP burn required
- [x] Shared wallet across HeartBeets/DogPlanet/VibeIn (linked to Google account)
