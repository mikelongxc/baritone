package dev.autostore.core;

import java.util.*;

/** Keep starting supplies; deposit whole ordinary stacks acquired during selection clearing. */
public final class ClearAreaInventory {
    private final Map<String,Integer> starting = new HashMap<>();
    public void reset(){starting.clear();}
    public void begin(List<Policy.Stack> inventory, Set<Integer> protectedSlots) {
        reset();
        for(int i=0;i<inventory.size();i++) {
            var s=inventory.get(i);
            if(!protectedSlots.contains(i) && !s.special() && !s.empty())starting.merge(s.item(),s.count(),Integer::sum);
        }
    }
    public boolean mayDeposit(Policy.Stack stack,List<Policy.Stack> inventory,Set<Integer> protectedSlots) {
        if(stack.empty() || stack.special())return false;
        int total=0;
        for(int i=0;i<inventory.size();i++) {
            var s=inventory.get(i);
            if(!protectedSlots.contains(i) && !s.special() && s.item().equals(stack.item()))total+=s.count();
        }
        return total-stack.count()>=starting.getOrDefault(stack.item(),0);
    }
}
