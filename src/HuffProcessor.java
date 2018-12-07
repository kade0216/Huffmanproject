import java.util.*;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		int[] counts = readForCounts(in);
	    HuffNode root = makeTreeFromCounts(counts);
	   	String[] codings = makeCodingsFromTree(root);
	   	
	   	out.writeBits(BITS_PER_INT, HUFF_TREE);
	   	writeHeader(root,out);
	   	
	   	in.reset();
	   	writeCompressedBits(codings,in,out);
	   	out.close();
	}
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){

		int bits = in.readBits(BITS_PER_INT);
		if(bits != HUFF_TREE) {
			throw new HuffException("invalid header beginning with: " + bits);
		}
		
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root,in,out);
		out.close();
	}
	
	private HuffNode readTreeHeader(BitInputStream in) {
		int i = in.readBits(1);
        if (i == -1) {
            throw new HuffException("not valid input, no PSEUDO_EOF");
        }
        else if (i == 1){
			HuffNode lf = new HuffNode(in.readBits(9),i);
			return lf; 
		}
		else {
			HuffNode l = readTreeHeader(in);
			HuffNode r = readTreeHeader(in);
			return new HuffNode(0, 0, l, r);
		}
	}
	
	private void readCompressedBits(HuffNode n, BitInputStream in, BitOutputStream out) {
	       HuffNode first = n;
	       while (true) {
	           int bits = in.readBits(1);
	           
	           if (bits == -1) {
	               throw new HuffException("not valid input, no PSEUDO_EOF");
	           }
	           
	           else { 
	               if (bits == 0) {
	            	   first = first.myLeft; 
	            	   bits = 0;
	               }
	               if (bits != 0) {
	            	   first = first.myRight;
	               }
	               
	               
	               if (first == null){
	        		   break;
	        	   }
	               
	               if (first.myRight == null && first.myLeft == null) {
	                   if (first.myValue == PSEUDO_EOF) {
	                       break;
	                   }
	                   else {
	                       out.writeBits(BITS_PER_WORD, first.myValue);
	                       first = n;
	                   }
	               }
	           }
	       }
	}
	
	private int[] readForCounts(BitInputStream in){
		int[] arr = new int[256];
		
        while (true) {
            int num = in.readBits(BITS_PER_WORD);
            
            if (num == -1) {
        		break;
        	}
            
            arr[num]++;
        }
        
		return arr;
	}
	
	private HuffNode makeTreeFromCounts(int[] arr){
        PriorityQueue<HuffNode> pq = new PriorityQueue<>();
        
        for (int index = 0; index < arr.length; index++){
        	if (arr[index] > 0){
        		pq.add(new HuffNode(index, arr[index], null, null));
        	}
        }
        
        pq.add(new HuffNode(PSEUDO_EOF, 1));
       
        while (pq.size() > 1) {
            HuffNode left = pq.remove();
            HuffNode right = pq.remove();
            // create new HuffNode t with weight from
            // left.weight+right.weight and left, right subtrees
            int weight = left.myWeight + right.myWeight;
            HuffNode t = new HuffNode(-1, weight, left, right);
            pq.add(t);
        }
        
        HuffNode root = pq.remove();
		return root;
	}
	
	private String[] makeCodingsFromTree(HuffNode root){
		String[] encodings = new String[257];
		codingHelper(root,"", encodings);
		return encodings;
	}
	
	private void codingHelper(HuffNode t, String path, String[] s){
		HuffNode current = t;
		
		if (current.myLeft == null && current.myRight == null) {
			s[current.myValue] = path;
		}
		
		else {
			codingHelper(current.myLeft, path + "0", s);
			codingHelper(current.myRight, path + "1", s);
		}
	}
	
	private void writeHeader(HuffNode root, BitOutputStream out){
		HuffNode current = root;
		
		if(current.myRight == null && current.myLeft == null) {
			out.writeBits(1, 1);	
			out.writeBits(9, current.myValue);
			return;
		}
		
		out.writeBits(1,0);
		writeHeader(current.myLeft, out);
		writeHeader(current.myRight, out);
	}
	
	private void writeCompressedBits(String[] codes, BitInputStream in,BitOutputStream out){
		int val = 0;
		
		while (val != -1) {
			val = in.readBits(BITS_PER_WORD);
			
			if (val == -1){
				break;
			}
			
			String s = codes[val];		
			out.writeBits(s.length(), Integer.parseInt(s, 2));
		}
		
		if (codes[256] != "") {
			out.writeBits(codes[256].length(), Integer.parseInt(codes[256], 2));
		}
	}

}