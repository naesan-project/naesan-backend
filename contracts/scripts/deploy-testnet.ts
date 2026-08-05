import { network } from "hardhat";
import { isAddress, zeroAddress } from "viem";

const { viem, networkName } = await network.create();

if (networkName !== "sepolia") {
  throw new Error("Public testnet deployment must target the sepolia network.");
}

const writer = process.env.NAESAN_ANCHOR_WRITER_ADDRESS;
if (writer === undefined || !isAddress(writer) || writer === zeroAddress) {
  throw new Error("NAESAN_ANCHOR_WRITER_ADDRESS must be a non-zero address.");
}

const [deployer] = await viem.getWalletClients();

console.log(`Deploying ProofCommitmentAnchor to ${networkName}...`);
console.log(`Deployer: ${deployer.account.address}`);
console.log(`Writer: ${writer}`);

const anchor = await viem.deployContract("ProofCommitmentAnchor", [writer]);

console.log(`ProofCommitmentAnchor address: ${anchor.address}`);
