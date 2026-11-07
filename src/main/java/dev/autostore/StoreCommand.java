package dev.autostore;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import dev.autostore.core.Loop;
import java.util.List;
import java.util.stream.Stream;

/** One persistent destination registration; normal #mine starts the loop. */
public final class StoreCommand extends Command {
    public StoreCommand(IBaritone baritone) { super(baritone, "store"); }
    @Override public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(2);
        Controller c=Controller.INSTANCE;
        if(c==null) { logDirect("Storage is not initialized; check Meteor loaded successfully."); return; }
        String action=args.hasAny()?args.getString():"add";
        try {
            switch(action) {
                case "add" -> {
                    args.requireMax(1);
                    if(args.hasAny())c.addDepot(args.getString());else c.addDepot();
                    logDirect("Storage saved — "+c.registeredCount()+" location(s) total. Run #mine minecraft:sand.");
                }
                case "count" -> {
                    args.requireMax(0);logDirect(c.registeredCount()+" storage location(s) saved.");
                }
                case "clear" -> {
                    args.requireMax(1);String id=args.hasAny()?args.getString():null;
                    int removed=c.clearDepots(id);
                    logDirect("Cleared "+removed+" storage location(s); "+c.registeredCount()+" remaining. Managed mining stopped.");
                }
                case "status" -> {
                    args.requireMax(0);
                    if(c.profile==null)c.load();
                    logDirect(c.toolStatus());
                    logDirect(c.recoveryStatus());
                    logDirect(c.loop.state+": "+c.statusReason()+" | "+c.profile.depots.size()+" registered containers");
                }
                case "remove" -> {
                    args.requireExactly(1);String id=args.getString();c.editable();
                    boolean removed=c.profile.depots.removeIf(d -> d.id.equals(id));c.save();
                    logDirect((removed?"Storage removed. ":"No storage with that name. ")+c.registeredCount()+" location(s) remaining.");
                }
                case "list" -> {
                    args.requireMax(0);if(c.profile==null)c.load();
                    logDirect(c.registeredCount()+" storage location(s) saved:");
                    for(var d:c.profile.depots)logDirect(d.id+" | "+d.block+" | "+d.status);
                }
                default -> logDirect("Look at a chest or placed shulker and run #store. Also: #store status, #store list, #store count, #store clear [name].");
            }
        } catch(RuntimeException failure) { logDirect(failure instanceof Loop.SafetyException?failure.getMessage():"Storage command failed; no location details logged."); }
    }
    @Override public Stream<String> tabComplete(String label,IArgConsumer args) { return Stream.of("add","status","list","count","clear","remove"); }
    @Override public String getShortDesc() { return "Register looked-at storage for automatic mining deposits"; }
    @Override public List<String> getLongDesc() { return List.of("Stand where you can open a chest or placed shulker and run #store.","Run #mine minecraft:sand; deposits and mining resume automatically.","#stop stops; #pause and #resume temporarily stop/restart.","#store count/list/status show saved locations without coordinates.","#store clear removes all saved locations; #store clear <name> removes one and stops managed mining."); }
}
