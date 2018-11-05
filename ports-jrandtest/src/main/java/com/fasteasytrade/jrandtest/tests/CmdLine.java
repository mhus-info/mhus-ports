/*
 * Created on 19/02/2005
 *
 * JRandTest package
 *
 * Copyright (c) 2005, Zur Aougav, aougav@hotmail.com
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, 
 * are permitted provided that the following conditions are met:
 * 
 * Redistributions of source code must retain the above copyright notice, this list 
 * of conditions and the following disclaimer. 
 * 
 * Redistributions in binary form must reproduce the above copyright notice, this 
 * list of conditions and the following disclaimer in the documentation and/or 
 * other materials provided with the distribution. 
 * 
 * Neither the name of the JRandTest nor the names of its contributors may be 
 * used to endorse or promote products derived from this software without specific 
 * prior written permission. 
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND 
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR 
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES 
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON 
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT 
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS 
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.fasteasytrade.jrandtest.tests;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.logging.Logger;

import com.fasteasytrade.jrandtest.io.AlgoRandomStream;
import com.fasteasytrade.jrandtest.io.FileRandomStream;
import com.fasteasytrade.jrandtest.io.HttpGetUrlRandomStream;

/**
 * Commnad line class to read from console / end-user the filename /
 * algorithmname and testname to be executed. <p> Generally, we will try to
 * keep similar functions and options as in Gui class.
 * 
 * @author Zur Aougav
 */
public class CmdLine {

    final private Logger log = Logger.getLogger(getClass().getName());

    public final static String[] cardNames = { 
    		"Monte Carlo", 
    		"Count 1 Bit", 
    		"Count 2 Bits", 
    		"Count 3 Bits", 
    		"Count 4 Bits", 
    		"Count 8 Bits", 
    		"Count 16 Bits", 
    		"Count The 1s",
    		"Count The 1s Specific Bytes",
    		"Run",
    		"Squeeze"
    	};

    public final static String[] cardClassNames = { 
    		"com.fasteasytrade.jrandtest.tests.MonteCarlo", 
    		"com.fasteasytrade.jrandtest.tests.Count1Bit", 
    		"com.fasteasytrade.jrandtest.tests.Count2Bits",
    		"com.fasteasytrade.jrandtest.tests.Count3Bits", 
    		"com.fasteasytrade.jrandtest.tests.Count4Bits", 
    		"com.fasteasytrade.jrandtest.tests.Count8Bits", 
    		"com.fasteasytrade.jrandtest.tests.Count16Bits", 
    		"com.fasteasytrade.jrandtest.tests.CountThe1s", 
    		"com.fasteasytrade.jrandtest.tests.CountThe1sSpecificBytes", 
    		"com.fasteasytrade.jrandtest.tests.Run", 
    		"com.fasteasytrade.jrandtest.tests.Squeeze"
    	};

    public final static String[] algoNames = { 
    		"None", 
    		"ARC4", 
    		"MT19937", 
    		"BlowFish", 
    		"RSA", 
    		"JavaRandom", 
    		"JavaSecuredRandom",
    		"AES",
    		"BBS",
    		"CubicResidue",
    		"Lcg1",
    		"MicaliSchnorr"
    	};

    public final static String[] algoClassNames = { 
    		"None", 
    		"com.fasteasytrade.jrandtest.algo.ARC4", 
    		"com.fasteasytrade.jrandtest.algo.MT19937", 
    		"com.fasteasytrade.jrandtest.algo.BlowFish", 
    		"com.fasteasytrade.jrandtest.algo.RSA", 
    		"com.fasteasytrade.jrandtest.algo.JavaRandom", 
    		"com.fasteasytrade.jrandtest.algo.JavaSecuredRandom",
    		"com.fasteasytrade.jrandtest.algo.AES",
    		"com.fasteasytrade.jrandtest.algo.BBS",
    		"com.fasteasytrade.jrandtest.algo.CubicResidue",
    		"com.fasteasytrade.jrandtest.algo.Lcg1",
    		"com.fasteasytrade.jrandtest.algo.MicaliSchnorr"
    	};

    // classes are included here just to ease compile... not used anywhere!
    Run r1;

    Count1Bit r2;

    Count2Bits r3;

    Count3Bits r4;

    Count4Bits r5;

    Count8Bits r6;

    Count16Bits r7;

    MonteCarlo r8;

    Squeeze r9;

    MinimumDistance r10;

    CountThe1s r11;

    CountThe1sSpecificBytes r12;

    BirthdaySpacings r13;

    BinaryRankTestFor6x8Matrices r14;

    BinaryRankTestFor31x31Matrices r15;

    BinaryRankTestFor32x32Matrices r16;

    Overlapping20TuplesBitstream r17;

    OverlappingPairsSparseOccupancy r18;

    OverlappingQuadruplesSparseOccupancy r19;

    DNA r20;

    /**
     * print copyrights to console.
     * 
     */
    public static void printCopyrights() {
        System.out.println("JRandTest (C) Zur Aougav <aougav@hotmail.com>, 2005");
    }

    /**
     * Simple session: <br> 1. get input filename/algorithm for all tests
     * (or "exit") <br> 2. repeat till "exit" <br> 2.1 display list of
     * tests <br> 2.2 get requested test number <br> 2.3 run the test on
     * the input file <br>
     * 
     * @param args not used.
     * @throws Exception generally are grabbed, but some I/O Exceptions are
     *             thrown.
     */
    public void runCmd(String[] args) throws Exception {
        printCopyrights();

        /**
         * load algorithms' names
         */

        String line = null;
        BufferedReader dis = new BufferedReader(new InputStreamReader(System.in));

        String filename = null;
        File file;

        int algoNumber = -1; // -1 is None

        do {

            /*
             * select algorithm, if any
             */
            do {
                System.out.println("Specify algorithm number to be run on input file (\"none\" or \"exit\" to exit):");
                System.out.println(" 0. None");
                for (int i = 0; i < algoNames.length; i++) {
                    if ((i + 1) < 10) {
                        System.out.println(" " + (i + 1) + ". " + algoNames[i]);
                    } else {
                        System.out.println("" + (i + 1) + ". " + algoNames[i]);
                    }
                }

                line = dis.readLine().trim(); // take input from end-user

                if (line == null) {
                    return;
                }

                if (line.length() == 0) {
                    continue;
                }

                if ("exit".equals(line.toLowerCase()) || "quit".equals(line.toLowerCase())) { // exit?
                    System.out.println("Byte.");
                    return;
                }

                /*
                 * no algorithm?
                 */
                if (line.startsWith("None") || line.startsWith("none")) {
                    algoNumber = -1;
                    break;
                }

                try {
                    algoNumber = Integer.parseInt(line) - 1;
                    if (-1 <= algoNumber && algoNumber < algoNames.length) {
                        break;
                    }
                } catch (Exception e) {
                    System.out.println("Error: " + e);
                }
            } while (true);

            do {
                System.out.println("Specify filename (\"none\" or \"exit\" to exit):");

                line = dis.readLine().trim(); // take input from end-user

                if (line == null) {
                    return;
                }

                if (line.length() == 0) {
                    continue;
                }

                if ("exit".equals(line.toLowerCase()) || "quit".equals(line.toLowerCase())) { // exit?
                    System.out.println("Byte.");
                    return;
                }

                filename = line;

                /*
                 * no file?
                 */
                if (filename.startsWith("None") || filename.startsWith("none")) {
                    filename = null;
                    break;
                }

                try {
                    /*
                     * is it a file?
                     */
                    file = new File(filename);
                    if (file.exists()) {
                        break;
                    }
                    System.out.println("File " + filename + " not found.");

                } catch (Exception e) {
                    System.out.println("Error: " + e);
                }
            } while (true);

            if (filename != null || algoNumber > -1) {
                break;
            }
            System.out.println("You must specify algorithm name and/or filename");
        } while (true);

        /**
         * At this point we have algorithm name or filename (or both)
         */

        /**
         * run several tests on the same input file
         */
        do {
            int testNumber = -1;

            do {
                System.out.println("Specify test number to be run on algorithm / input file (or \"exit\" to exit):");
                for (int i = 0; i < cardNames.length; i++) {
                    if ((i + 1) < 10) {
                        System.out.println(" " + (i + 1) + ". " + cardNames[i]);
                    } else {
                        System.out.println("" + (i + 1) + ". " + cardNames[i]);
                    }
                }

                line = dis.readLine().trim(); // take input from end-user

                if (line == null) {
                    return;
                }

                if (line.length() == 0) {
                    continue;
                }

                if ("exit".equals(line.toLowerCase()) || "quit".equals(line.toLowerCase())) { // exit?
                    System.out.println("Byte.");
                    return;
                }

                try {
                    testNumber = Integer.parseInt(line) - 1;
                    if (0 <= testNumber && testNumber < cardNames.length) {
                        break;
                    }
                } catch (Exception e) {
                    System.out.println("Error: " + e);
                }
            } while (true);

            // run test...
            String classname = cardClassNames[testNumber];
            Base ob = null;
            try {
                ob = (Base)Class.forName(classname).newInstance();
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (ob == null) {
                return;
            }

            String algoname = "";
            if (filename == null) {
                filename = "";
            }

            try {
                if (algoNumber > -1) {
                    algoname = algoNames[algoNumber];
                    AlgoRandomStream rs = null;
                    classname = algoClassNames[algoNumber];
                    System.out.println("algorithm: " + algoname + " from " + classname);
                    try {
                        rs = (AlgoRandomStream)Class.forName(classname).newInstance();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (rs == null) {
                    		return;
                    }
                    rs.setupKeys();

                    /*
                     * set input file to algorthm, if any
                     */
                    if (filename.length() > 0) {
                        rs.setFilename(filename);
                    }

                    /*
                     * init algorithm
                     */
                    //rs.setup();
                    /*
                     * make algo as input to test
                     */
                    ob.registerInput(rs);

                } else if (filename.toUpperCase().startsWith("HTTP://")) {
                    ob.registerInput(new HttpGetUrlRandomStream(filename));
                } else {
                    ob.registerInput(new FileRandomStream(filename));
                }

                /*
                 * run test!
                 */
                ob.test(null);

            } catch (Exception e) {
                e.printStackTrace();
                log.info("" + e);
            }

        } while (true); // run several tests on the the same algorithm / input file
    }

    public static void main(String[] args) {
        CmdLine cl = new CmdLine();

        try {
            cl.runCmd(args);
        } catch (Exception e) {
            System.out.println("Sorry. Error while processing CmdLine.");
            e.printStackTrace();
            System.out.println(e);
        }
    }
}