package dev.autostore.trail;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import java.util.List;
import java.util.stream.Stream;

public final class TrailCommand extends Command {
    public TrailCommand(IBaritone bot){super(bot,"trail");}
    @Override public void execute(String label,IArgConsumer args)throws CommandException {
        args.requireMax(1);String action=args.hasAny()?args.getString():"start";
        TrailController t=TrailController.INSTANCE;
        if(t==null){logDirect("Trail controller has not initialized");return;}
        try {
            switch(action) {
                case "start"->t.start(false);
                case "new"->{t.stop();t.start(true);}
                case "stop"->t.stop();
                case "pause"->t.pause();
                case "resume"->{if(!t.resume())t.start(false);}
                case "status"->logDirect(t.status());
                default->logDirect("#trail [start|new|pause|resume|stop|status]");
            }
        } catch(IllegalStateException e){logDirect(e.getMessage());}
        catch(Exception e){logDirect("Trail could not start. Check Trouser Streak 1.7.0 and the local checkpoint; no location details logged.");}
    }
    @Override public Stream<String> tabComplete(String label,IArgConsumer args){return Stream.of("start","new","pause","resume","stop","status");}
    @Override public String getShortDesc(){return "Follow Trouser Streak chunk trails with Elytra and supply-shulker refueling";}
    @Override public List<String> getLongDesc(){return List.of("Carry Elytra, rockets, a fireworks-only shulker, a healthy pickaxe and a free inventory slot.","Look along a trail and run #trail. #trail new starts a fresh route; #trail start resumes saved evidence.","#trail pause lands; #trail resume continues. #stop / #trail stop cancel immediately.","Supply shulkers are recovered before departure. Status hides locations. No ender-chest access in this version.");}
}
