/**
 * Copyright 2013 Ronald W Hoffman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package BitcoinWallet;

import java.io.EOFException;
import java.io.InputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * <p>The 'merkleblock' message is sent in response to a 'getdata' block request
 * and the requesting peer has set a Bloom filter.  In this case, the response is just the
 * block header and a Merkle branch representing the matching transactions.</p>
 *
 * <p>MerkleBlock Message</p>
 * <pre>
 *   Size       Field           Description
 *   ====       =====           ===========
 *   4 bytes    Version         The block version number
 *   32 bytes   PrevBlockHash   The hash of the preceding block in the chain
 *   32 byte    MerkleRoot      The Merkle root for the transactions in the block
 *   4 bytes    Time            The time the block was mined
 *   4 bytes    Difficulty      The target difficulty
 *   4 bytes    Nonce           The nonce used to generate the required hash
 *   4 bytes    txCount         Number of transactions in the block
 *   VarInt     hashCount       Number of hashes
 *   Variable   hashes          Hashes in depth-first order
 *   VarInt     flagCount       Number of bytes of flag bits
 *   Variable   flagBits        Flag bits packed 8 per byte, least significant bit first
 * </pre>
 */
public class MerkleBlockMessage {

    /**
     * Processes the 'merkleblock' message
     *
     * @param       msg                     Message
     * @param       inStream                Input stream
     * @throws      EOFException            End-of-data processing input stream
     * @throws      IOException             Unable to read input stream
     * @throws      VerificationException   Verification error
     */
    public static void processMerkleBlockMessage(Message msg, InputStream inStream)
                                    throws EOFException, IOException, VerificationException {
        //
        // Get the block header
        //
        byte[] hdrData = new byte[BlockHeader.HEADER_SIZE];
        int count = inStream.read(hdrData);
        if (count != BlockHeader.HEADER_SIZE)
            throw new EOFException("End-of-data processing 'merkleblock' data");
        BlockHeader blockHeader = new BlockHeader(hdrData);
        //
        // Get the matching transactions from the Merkle branch
        //
        MerkleBranch merkleBranch = new MerkleBranch(inStream);
        List<Sha256Hash> matches = new LinkedList<>();
        Sha256Hash merkleRoot = merkleBranch.calculateMerkleRoot(matches);
        if (!merkleRoot.equals(blockHeader.getMerkleRoot()))
            throw new VerificationException("Merkle root is incorrect", Parameters.REJECT_INVALID);
        blockHeader.setMatches(matches);
        //
        // Remove the request from the processed queue
        //
        synchronized(Parameters.lock) {
            Iterator<PeerRequest> it = Parameters.processedRequests.iterator();
            while (it.hasNext()) {
                PeerRequest request = it.next();
                if (request.getType() == Parameters.INV_FILTERED_BLOCK &&
                                                request.getHash().equals(blockHeader.getHash())) {
                    it.remove();
                    break;
                }
            }
        }
        //
        // Queue the block for processing by the database handler
        //
        try {
            Parameters.databaseQueue.put(blockHeader);
        } catch (InterruptedException exc) {
            // Should never happen
        }
    }
}
