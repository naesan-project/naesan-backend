// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

/// @title Naesan proof commitment anchor
/// @notice Stores only salted bytes32 commitments and their first anchor time.
/// @dev The immutable writer prevents third parties from front-running known commitments.
contract ProofCommitmentAnchor {
    error InvalidWriter();
    error UnauthorizedWriter(address caller);
    error ZeroCommitment();
    error CommitmentAlreadyAnchored(bytes32 commitment);

    event CommitmentAnchored(bytes32 indexed commitment, uint64 anchoredAt);

    struct AnchorRecord {
        uint64 anchoredAt;
        bool exists;
    }

    address public immutable writer;
    mapping(bytes32 commitment => AnchorRecord record) private anchors;

    constructor(address writer_) {
        if (writer_ == address(0)) {
            revert InvalidWriter();
        }
        writer = writer_;
    }

    /// @notice Records a commitment exactly once.
    /// @param commitment The salted 32-byte commitment produced by the backend.
    function anchor(bytes32 commitment) external {
        if (msg.sender != writer) {
            revert UnauthorizedWriter(msg.sender);
        }
        if (commitment == bytes32(0)) {
            revert ZeroCommitment();
        }

        AnchorRecord storage record = anchors[commitment];
        if (record.exists) {
            revert CommitmentAlreadyAnchored(commitment);
        }

        uint64 anchoredAt = uint64(block.timestamp);
        record.anchoredAt = anchoredAt;
        record.exists = true;

        emit CommitmentAnchored(commitment, anchoredAt);
    }

    /// @notice Looks up whether a commitment exists and when it was anchored.
    function lookup(bytes32 commitment) external view returns (bool exists, uint64 anchoredAt) {
        AnchorRecord storage record = anchors[commitment];
        return (record.exists, record.anchoredAt);
    }
}
