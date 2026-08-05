import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { network } from "hardhat";
import {
  getAddress,
  keccak256,
  stringToHex,
  type Address,
  type Hash,
  zeroAddress,
  zeroHash,
} from "viem";

describe("ProofCommitmentAnchor", async function () {
  const { viem, networkHelpers } = await network.create();
  const publicClient = await viem.getPublicClient();

  async function deployFixture() {
    const [writer, stranger] = await viem.getWalletClients();
    const anchor = await viem.deployContract("ProofCommitmentAnchor", [
      writer.account.address,
    ]);

    return { anchor, writer, stranger };
  }

  it("returns an empty lookup result before anchoring", async function () {
    const { anchor } = await networkHelpers.loadFixture(deployFixture);
    const commitment = keccak256(stringToHex("not-anchored"));

    assert.deepEqual(await anchor.read.lookup([commitment]), [false, 0n]);
  });

  it("stores the first block timestamp and emits an audit event", async function () {
    const { anchor } = await networkHelpers.loadFixture(deployFixture);
    const commitment = keccak256(stringToHex("naesan-proof"));

    const transactionHash = await anchor.write.anchor([commitment]);
    const receipt = await publicClient.waitForTransactionReceipt({
      hash: transactionHash,
    });
    const block = await publicClient.getBlock({
      blockNumber: receipt.blockNumber,
    });

    assert.deepEqual(await anchor.read.lookup([commitment]), [
      true,
      block.timestamp,
    ]);

    const events = await publicClient.getContractEvents({
      address: anchor.address,
      abi: anchor.abi,
      eventName: "CommitmentAnchored",
      fromBlock: receipt.blockNumber,
      toBlock: receipt.blockNumber,
      strict: true,
    });

    assert.equal(events.length, 1);
    const eventArgs = events[0].args as unknown as {
      commitment: Hash;
      anchoredAt: bigint;
    };
    assert.equal(eventArgs.commitment, commitment);
    assert.equal(eventArgs.anchoredAt, block.timestamp);
  });

  it("rejects the zero commitment", async function () {
    const { anchor } = await networkHelpers.loadFixture(deployFixture);

    await assert.rejects(anchor.write.anchor([zeroHash]), /ZeroCommitment/);
    assert.deepEqual(await anchor.read.lookup([zeroHash]), [false, 0n]);
  });

  it("rejects a duplicate without changing the first anchor time", async function () {
    const { anchor } = await networkHelpers.loadFixture(deployFixture);
    const commitment = keccak256(stringToHex("duplicate-proof"));

    await anchor.write.anchor([commitment]);
    const firstLookup = await anchor.read.lookup([commitment]);

    await assert.rejects(
      anchor.write.anchor([commitment]),
      /CommitmentAlreadyAnchored/,
    );
    assert.deepEqual(await anchor.read.lookup([commitment]), firstLookup);
  });

  it("allows only the immutable writer to submit commitments", async function () {
    const { anchor, writer, stranger } =
      await networkHelpers.loadFixture(deployFixture);
    const commitment = keccak256(stringToHex("protected-proof"));

    assert.equal(
      getAddress((await anchor.read.writer()) as Address),
      getAddress(writer.account.address),
    );
    await assert.rejects(
      anchor.write.anchor([commitment], { account: stranger.account }),
      /UnauthorizedWriter/,
    );
    assert.deepEqual(await anchor.read.lookup([commitment]), [false, 0n]);
  });

  it("rejects a zero writer at deployment", async function () {
    await assert.rejects(
      viem.deployContract("ProofCommitmentAnchor", [zeroAddress]),
      /InvalidWriter/,
    );
  });
});
