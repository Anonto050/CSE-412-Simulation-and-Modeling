import java.io.*;
import java.util.Random;

public class InventorySimulation {

    static int amount, bigs, initialInvLevel, invLevel, nextEventType, numEvents,
            numMonths, numValuesDemand, smalls;
    static float areaHolding, areaShortage, holdingCost, incrementalCost, maxLag,
            meanInterDemand, minLag, probDistribDemand[], setupCost,
            shortageCost, simTime, timeLastEvent, timeNextEvent[],
            totalOrderingCost;

    static BufferedReader infile;
    static BufferedWriter outfile;

    public static void main(String[] args) throws IOException {
        // Part 1: Initialization and input parsing
        int numPolicies = initializeSimulation();  // Get numPolicies from initialization

        // Part 2: Running the simulation
        runSimulation(numPolicies);  // Pass numPolicies to runSimulation()
    }


    public static int initializeSimulation() throws IOException {
        // Open input and output files.
        infile = new BufferedReader(new FileReader("in.txt"));
        outfile = new BufferedWriter(new FileWriter("out.txt"));

        // Specify the number of events for the timing function.
        numEvents = 4;

        // Read input parameters.
        String[] inputParams = infile.readLine().split(" ");
        initialInvLevel = Integer.parseInt(inputParams[0]);
        numMonths = Integer.parseInt(inputParams[1]);
        int numPolicies = Integer.parseInt(inputParams[2]);  // Return this value

        String[] demandParams = infile.readLine().split(" ");
        numValuesDemand = Integer.parseInt(demandParams[0]);
        meanInterDemand = Float.parseFloat(demandParams[1]);

        String[] costParams = infile.readLine().split(" ");
        setupCost = Float.parseFloat(costParams[0]);
        incrementalCost = Float.parseFloat(costParams[1]);
        holdingCost = Float.parseFloat(costParams[2]);
        shortageCost = Float.parseFloat(costParams[3]);

        String[] lagParams = infile.readLine().split(" ");
        minLag = Float.parseFloat(lagParams[0]);
        maxLag = Float.parseFloat(lagParams[1]);

        probDistribDemand = new float[numValuesDemand + 1];
        String[] probValues = infile.readLine().split(" ");
        for (int i = 1; i <= numValuesDemand; i++) {
            probDistribDemand[i] = Float.parseFloat(probValues[i - 1]);
        }

        // Write report heading and input parameters.
        writeHeader(numPolicies);

        return numPolicies;  // Return numPolicies for use in runSimulation()
    }


    public static void runSimulation(int numPolicies) throws IOException {
        // Run the simulation for each policy.
        for (int i = 1; i <= numPolicies; i++) {
            String[] policyInput = infile.readLine().split(" ");
            smalls = Integer.parseInt(policyInput[0]);
            bigs = Integer.parseInt(policyInput[1]);

            initialize();

            // Run the simulation until it terminates after an end-simulation event (type 3) occurs.
            do {
                timing();
                updateTimeAvgStats();

                // Invoke the appropriate event function.
                switch (nextEventType) {
                    case 1:
                        orderArrival();
                        break;
                    case 2:
                        demand();
                        break;
                    case 4:
                        evaluate();
                        break;
                    case 3:
                        report();
                        break;
                }

            } while (nextEventType != 3);
        }

        // End the simulations.
        infile.close();
        outfile.close();
    }




    static void writeHeader(int numPolicies) throws IOException {
        outfile.write("------Single-Product Inventory System------\n\n");
        outfile.write(String.format("Initial inventory level: %d items\n\n", initialInvLevel));
        outfile.write(String.format("Number of demand sizes: %d\n\n", numValuesDemand));
        outfile.write("Distribution function of demand sizes: ");
        for (int i = 1; i <= numValuesDemand; i++) {
            outfile.write(String.format("%.2f ", probDistribDemand[i]));
        }
        outfile.write(String.format("\n\nMean inter-demand time: %.2f months\n\n", meanInterDemand));
        outfile.write(String.format("Delivery lag range: %.2f to %.2f months\n\n", minLag, maxLag));
        outfile.write(String.format("Length of simulation: %d months\n\n", numMonths));
        outfile.write("Costs:\n");
        outfile.write(String.format("K = %.2f\n", setupCost));
        outfile.write(String.format("i = %.2f\n", incrementalCost));
        outfile.write(String.format("h = %.2f\n", holdingCost));
        outfile.write(String.format("pi = %.2f\n\n", shortageCost));
        outfile.write(String.format("Number of policies: %d\n\n", numPolicies));
        outfile.write("Policies:\n");
        outfile.write("--------------------------------------------------------------------------------------------------\n");
        outfile.write(" Policy        Avg_total_cost     Avg_ordering_cost      Avg_holding_cost     Avg_shortage_cost\n");
        outfile.write("--------------------------------------------------------------------------------------------------\n");
    }

    static void initialize() {
        invLevel = initialInvLevel;
        simTime = 0.0f;
        timeLastEvent = 0.0f;
        areaHolding = 0.0f;
        areaShortage = 0.0f;
        totalOrderingCost = 0.0f;
        timeNextEvent = new float[numEvents + 1];

        // Schedule the first demand.
        timeNextEvent[2] = simTime + expon(meanInterDemand);

        // Schedule the first inventory evaluation.
        timeNextEvent[4] = simTime + 1.0f;

        // Set the time for the next order arrival event to be "infinite" (i.e., no order is currently outstanding).
        timeNextEvent[1] = Float.MAX_VALUE;

        // Schedule the end of the simulation at the end of the specified number of months (Event Type 3).
        timeNextEvent[3] = numMonths;  // Event 3 will occur at the end of the simulation.
    }


    static void timing() {
        float minTimeNextEvent = Float.MAX_VALUE;

        nextEventType = 0;

        // Determine the next event.
        for (int i = 1; i <= numEvents; ++i) {
            if (timeNextEvent[i] < minTimeNextEvent) {
                minTimeNextEvent = timeNextEvent[i];
                nextEventType = i;
            }
        }

        // Advance the simulation clock.
        simTime = minTimeNextEvent;
    }

    static void orderArrival() {
        // Increment the inventory level by the amount ordered.
        invLevel += amount;

        // Since no order is now outstanding, eliminate the order-arrival event from consideration.
        timeNextEvent[1] = Float.MAX_VALUE;
    }

    static void demand() {
        // Decrement the inventory level by a generated demand size.
        invLevel -= randomInteger(probDistribDemand);

        // Schedule the time of the next demand.
        timeNextEvent[2] = simTime + expon(meanInterDemand);
    }

    // Evaluate the inventory level and perform the necessary actions.
    static void evaluate() {
        // Check if inventory level is less than the reorder point
        if (isInventoryBelowSmalls()) {
            // Place an order to restock the inventory
            placeOrder();
        }

        // Schedule the next inventory evaluation
        scheduleNextEvaluation();
    }

    // Check whether the inventory level is below the reorder point (smalls)
    static boolean isInventoryBelowSmalls() {
        return invLevel < smalls;
    }

    // Place an order for the necessary amount to bring inventory to the bigs level
    static void placeOrder() {
        // Calculate the order amount needed to replenish the inventory
        amount = bigs - invLevel;

        // Add the setup cost and incremental cost for the order
        totalOrderingCost += setupCost + incrementalCost * amount;

        // Schedule the order arrival using a random lag time
        timeNextEvent[1] = simTime + uniform(minLag, maxLag);
    }

    // Schedule the next evaluation of the inventory level
    static void scheduleNextEvaluation() {
        timeNextEvent[4] = simTime + 1.0f;
    }

    static void report() throws IOException {
        // Compute and write estimates of desired measures of performance.
        float avgHoldingCost, avgOrderingCost, avgShortageCost;
        avgOrderingCost = totalOrderingCost / numMonths;
        avgHoldingCost = holdingCost * areaHolding / numMonths;
        avgShortageCost = shortageCost * areaShortage / numMonths;

        // Increase the width specifier from 15 to 20 for more spacing.
        outfile.write(String.format("\n\n(%3d,%3d)%20.2f%20.2f%20.2f%20.2f",
                smalls, bigs, avgOrderingCost + avgHoldingCost + avgShortageCost,
                avgOrderingCost, avgHoldingCost, avgShortageCost));
    }


    static void updateTimeAvgStats() {
        float timeSinceLastEvent = simTime - timeLastEvent;
        timeLastEvent = simTime;

        // Update areas for time-average statistics.
        if (invLevel < 0) {
            areaShortage -= invLevel * timeSinceLastEvent;
        } else if (invLevel > 0) {
            areaHolding += invLevel * timeSinceLastEvent;
        }
    }

    static int randomInteger(float[] probDistrib) {
        Random random = new Random();
        float u = random.nextFloat();

        int i;
        for (i = 1; u >= probDistrib[i]; ++i) ;
        return i;
    }

    static float uniform(float a, float b) {
        Random random = new Random();
        return a + random.nextFloat() * (b - a);
    }

    static float expon(float mean) {
        Random random = new Random();
        return (float) (-mean * Math.log(random.nextDouble()));
    }
}
