import java.io.*;
import java.util.Scanner;
import java.util.Random;

public class MM1Simulation {

    static final int Q_LIMIT = 100;  // Queue limit constant
    static final int IDLE = 0;       // Server idle status
    static final int BUSY = 1;       // Server busy status

    static float meanInterarrival, meanService;
    static int numDelaysRequired;

    static int numEvents = 2;  // Number of events

    static float simTime, timeLastEvent;
    static int serverStatus, numInQ, numCustsDelayed;
    static float totalOfDelays, areaNumInQ, areaServerStatus;
    static float[] timeNextEvent = new float[3];
    static float[] timeArrival = new float[Q_LIMIT + 1];
    static int eventCounter = 0; // Event counter to track total number of events

    // New counters for arrived and departed customers
    static int arrivedCounter = 0;
    static int departedCounter = 0;

    static BufferedWriter outfile;
    static Scanner infile;

    public static void main(String[] args) throws IOException {
        // Open input and output files
        infile = new Scanner(new File("in.txt"));
        outfile = new BufferedWriter(new FileWriter("out.txt"));

        // Read input parameters
        meanInterarrival = infile.nextFloat();
        meanService = infile.nextFloat();
        numDelaysRequired = infile.nextInt();

        // Write report heading and input parameters
        outfile.write("Single-server queueing system\n\n");
        outfile.write(String.format("Mean interarrival time%11.3f minutes\n\n", meanInterarrival));
        outfile.write(String.format("Mean service time%16.3f minutes\n\n", meanService));
        outfile.write(String.format("Number of customers%14d\n\n", numDelaysRequired));

        // Initialize the simulation
        initialize();

        // Run the simulation while more delays are still needed
        while (numCustsDelayed < numDelaysRequired) {
            // Determine the next event
            timing();

            // Update time-average statistical accumulators
            updateTimeAvgStats();

            // Invoke the appropriate event function
            switch (nextEventType()) {
                case 1:
                    arrive();
                    break;
                case 2:
                    depart();
                    break;
            }
        }

        // Invoke the report generator and end the simulation
        report();
        infile.close();
        outfile.close();
    }

    static void initialize() {
        simTime = 0.0f;  // Initialize the simulation clock

        // Initialize state variables
        serverStatus = IDLE;
        numInQ = 0;
        timeLastEvent = 0.0f;

        // Initialize statistical counters
        numCustsDelayed = 0;
        totalOfDelays = 0.0f;
        areaNumInQ = 0.0f;
        areaServerStatus = 0.0f;

        // Initialize event list
        timeNextEvent[1] = simTime + expon(meanInterarrival);
        timeNextEvent[2] = Float.MAX_VALUE;
    }

    // Function to determine the next event and update the event type and time
    static int getNextEvent() {
        float minTimeNextEvent = Float.MAX_VALUE;
        int nextEventType = 0;

        // Determine the event type of the next event to occur
        for (int i = 1; i <= numEvents; ++i) {
            if (timeNextEvent[i] < minTimeNextEvent) {
                minTimeNextEvent = timeNextEvent[i];
                nextEventType = i;
            }
        }

        // Check to see whether the event list is empty
        if (nextEventType == 0) {
            System.out.println(String.format("\nEvent list empty at time %f", simTime));
            System.exit(1);
        }

        // Return the type of the next event
        return nextEventType;
    }

    // The main timing function
    static void timing() throws IOException {
        // Get the next event type
        int nextEventType = getNextEvent();

        // Increment the event counter
        eventCounter++;

        // Log the next event to occur with proper arrived and departed counters
        if (nextEventType == 1) {
            arrivedCounter++; // Increment the arrived counter
            outfile.write(String.format("%d. Next event: Customer %d Arrival\n", eventCounter, arrivedCounter));
        } else if (nextEventType == 2) {
            departedCounter++; // Increment the departed counter
            outfile.write(String.format("%d. Next event: Customer %d Departure\n", eventCounter, departedCounter));
        }

        // Advance the simulation clock
        simTime = timeNextEvent[nextEventType];
    }

    // The main arrive function
    static void arrive() throws IOException {
        // Schedule the next customer arrival
        scheduleNextArrival();

        // Check the server status and take action accordingly
        if (serverStatus == BUSY) {
            handleBusyServer();
        } else {
            handleIdleServer();
        }
    }

    // Function to schedule the next customer arrival
    static void scheduleNextArrival() {
        timeNextEvent[1] = simTime + expon(meanInterarrival);
    }

    // Function to handle the case when the server is busy
    static void handleBusyServer() throws IOException {
        ++numInQ;

        // Check for queue overflow
        checkQueueOverflow();

        // Store the arrival time of the customer in the queue
        timeArrival[numInQ] = simTime;
    }

    // Function to check if the queue has overflowed
    static void checkQueueOverflow() throws IOException {
        if (numInQ > Q_LIMIT) {
            outfile.write(String.format("\nOverflow of the array time_arrival at time %f", simTime));
            System.exit(2);
        }
    }

    // Function to handle the case when the server is idle
    static void handleIdleServer() throws IOException {
        float delay = 0.0f;
        totalOfDelays += delay;
        ++numCustsDelayed;
        serverStatus = BUSY;

        // Only print "No. of customers delayed" after an arrival
        outfile.write(String.format("\n"));
        outfile.write(String.format("--------No. of customers delayed: %d--------\n", numCustsDelayed));
        outfile.write(String.format("\n"));

        // Schedule the departure of the current customer
        scheduleDeparture();
    }

    // Function to schedule the next customer departure
    static void scheduleDeparture() {
        timeNextEvent[2] = simTime + expon(meanService);
    }

    // The main depart function
    static void depart() throws IOException {
        // Check if the queue is empty or not and take appropriate action
        if (numInQ == 0) {
            handleEmptyQueue();
        } else {
            handleNonEmptyQueue();
        }
    }

    // Function to handle the case when the queue is empty
    static void handleEmptyQueue() {
        serverStatus = IDLE;
        timeNextEvent[2] = Float.MAX_VALUE;
    }

    // Function to handle the case when the queue is not empty
    static void handleNonEmptyQueue() throws IOException {
        --numInQ;

        // Calculate the delay for the next customer and update statistics
        float delay = simTime - timeArrival[1];
        totalOfDelays += delay;
        ++numCustsDelayed;

        // Only print "No. of customers delayed" after an arrival
        outfile.write(String.format("\n"));
        outfile.write(String.format("--------No. of customers delayed: %d--------\n", numCustsDelayed));
        outfile.write(String.format("\n"));

        // Schedule the next customer departure
        scheduleDeparture();

        // Move the remaining customers in the queue up one place
        shiftQueue();
    }

    // Function to shift the customers in the queue up by one
    static void shiftQueue() {
        System.arraycopy(timeArrival, 2, timeArrival, 1, numInQ);
    }

    static void report() throws IOException {
        outfile.write(String.format("\n\nAverage delay in queue%11.3f minutes\n\n", totalOfDelays / numCustsDelayed));
        outfile.write(String.format("Average number in queue%10.3f\n\n", areaNumInQ / simTime));
        outfile.write(String.format("Server utilization%15.3f\n\n", areaServerStatus / simTime));
        outfile.write(String.format("Time simulation ended%12.3f minutes", simTime));
    }

    static void updateTimeAvgStats() {
        float timeSinceLastEvent = simTime - timeLastEvent;
        timeLastEvent = simTime;

        // Update area under number-in-queue function
        areaNumInQ += numInQ * timeSinceLastEvent;

        // Update area under server-busy indicator function
        areaServerStatus += serverStatus * timeSinceLastEvent;
    }

    static float expon(float mean) {
        Random random = new Random();
        System.out.println("Random number: " + random.nextDouble());
        return (float) (-mean * Math.log(random.nextDouble()));
    }

    static int nextEventType() {
        int arrivalEvent = 1;
        int departureEvent = 2;

        if (timeNextEvent[arrivalEvent] < timeNextEvent[departureEvent]) {
            return arrivalEvent;
        } else {
            return departureEvent;
        }
    }
}


