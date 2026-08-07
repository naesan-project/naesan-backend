import { network } from "hardhat";
import { keccak256, stringToHex } from "viem";

const { viem, networkName } = await network.create();

if (networkName !== "localhost") {
  throw new Error("Gas measurement must target the localhost network.");
}

const [writer] = await viem.getWalletClients();
const publicClient = await viem.getPublicClient();
const { contract: anchor, deploymentTransaction } =
  await viem.sendDeploymentTransaction("ProofCommitmentAnchor", [
    writer.account.address,
  ]);
const deploymentReceipt = await publicClient.waitForTransactionReceipt({
  hash: deploymentTransaction.hash,
});
const commitment = keccak256(stringToHex("naesan-gas-measurement"));
const anchorTransaction = await anchor.write.anchor([commitment]);
const anchorReceipt = await publicClient.waitForTransactionReceipt({
  hash: anchorTransaction,
});

console.log(JSON.stringify({
  network: networkName,
  chainId: await publicClient.getChainId(),
  contractAddress: anchor.address,
  deployment: {
    transactionHash: deploymentTransaction.hash,
    gasUsed: deploymentReceipt.gasUsed.toString(),
    effectiveGasPriceWei: deploymentReceipt.effectiveGasPrice.toString(),
    feeWei: (
      deploymentReceipt.gasUsed * deploymentReceipt.effectiveGasPrice
    ).toString(),
  },
  anchor: {
    transactionHash: anchorTransaction,
    gasUsed: anchorReceipt.gasUsed.toString(),
    effectiveGasPriceWei: anchorReceipt.effectiveGasPrice.toString(),
    feeWei: (anchorReceipt.gasUsed * anchorReceipt.effectiveGasPrice).toString(),
  },
}, null, 2));
