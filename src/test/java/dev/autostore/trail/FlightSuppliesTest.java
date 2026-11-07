package dev.autostore.trail;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FlightSuppliesTest {
    @Test void reserveTriggersLandingBeforeExhaustion(){assertTrue(FlightSupplies.land(32,400,432));assertFalse(FlightSupplies.land(33,400,432));}
    @Test void wornElytraRequiresLanding(){assertTrue(FlightSupplies.land(200,44,432));assertFalse(FlightSupplies.land(200,45,432));}
    @Test void missingElytraNeverLaunches(){assertFalse(FlightSupplies.launch(200,0,0));assertTrue(FlightSupplies.land(200,0,0));}
    @Test void launchThresholdHasHysteresis(){assertFalse(FlightSupplies.launch(33,400,432));assertTrue(FlightSupplies.launch(64,400,432));}
    @Test void unhealthyPickaxeCannotRecoverBox(){assertFalse(FlightSupplies.toolUsable(98,2031));assertFalse(FlightSupplies.toolUsable(102,2031));assertTrue(FlightSupplies.toolUsable(103,2031));}
    @Test void transferRequiresConservationAndProgress(){assertTrue(FlightSupplies.transferConfirmed(128,32,64,96));assertFalse(FlightSupplies.transferConfirmed(128,32,128,32));assertFalse(FlightSupplies.transferConfirmed(128,32,64,32));assertFalse(FlightSupplies.transferConfirmed(128,32,64,160));}
}
