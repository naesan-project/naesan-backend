# Naesan proof contract

This Hardhat 3 project anchors Naesan proof commitments. It does not mint an
NFT, store evidence files, or assert product authenticity. The only on-chain
payload is a salted `bytes32` commitment and the timestamp of its first anchor.

## Access model

`ProofCommitmentAnchor` accepts writes from one immutable `writer` address. A
public write function would allow a third party to copy a pending commitment
from the mempool and anchor it first. An immutable writer prevents that without
introducing owner transfer, administrator, or role-management code. Changing a
compromised writer requires deploying a new contract and updating the backend
configuration.

## Verify

```shell
npm ci
npm run compile
npm test
npm run typecheck
```

## Local deployment

Run a local Hardhat node in one terminal:

```shell
npm run node
```

Deploy from another terminal. By default, the first local account is both the
deployer and writer. Set `NAESAN_ANCHOR_WRITER_ADDRESS` to test a separate
writer.

```shell
npm run deploy:local
```

## Sepolia deployment

Fund two dedicated Sepolia accounts: a one-time deployer and the backend writer.
Export the values below without committing them to Git:

```shell
export HARDHAT_VAR_SEPOLIA_RPC_URL='https://your-sepolia-rpc.example'
export HARDHAT_VAR_SEPOLIA_PRIVATE_KEY='0x...'
export NAESAN_ANCHOR_WRITER_ADDRESS='0x...'
npm run deploy:testnet
```

The script uses the production compiler profile and refuses to run against a
network other than Sepolia. It prints the contract address, deployment
transaction, and deployment block required by the backend configuration. Never
reuse the deployer key as a personal wallet key or commit private keys and RPC
credentials.
