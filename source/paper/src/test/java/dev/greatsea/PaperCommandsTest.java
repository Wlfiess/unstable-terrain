package dev.greatsea;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

final class PaperCommandsTest {
    private static CommandSender sender(boolean operator,Set<String> permissions,ArrayList<String> feedback){
        return (CommandSender)Proxy.newProxyInstance(PaperCommandsTest.class.getClassLoader(),new Class[]{CommandSender.class},(proxy,method,args)->switch(method.getName()){
            case "isOp"->operator;
            case "hasPermission"->permissions.contains((String)args[0]);
            case "sendMessage"->{if(args[0] instanceof String message)feedback.add(message);yield null;}
            default->throw new UnsupportedOperationException(method.getName());
        });
    }
    private static CommandSourceStack source(CommandSender sender){
        return (CommandSourceStack)Proxy.newProxyInstance(PaperCommandsTest.class.getClassLoader(),new Class[]{CommandSourceStack.class},(proxy,method,args)->{
            if(method.getName().equals("getSender"))return sender;
            if(method.getName().equals("getExecutor"))return null;
            throw new UnsupportedOperationException(method.getName());
        });
    }
    @SuppressWarnings("unchecked") private static CommandDispatcher<CommandSourceStack> registered(){
        var dispatcher=new CommandDispatcher<CommandSourceStack>();
        var registrar=(Commands)Proxy.newProxyInstance(PaperCommandsTest.class.getClassLoader(),new Class[]{Commands.class},(proxy,method,args)->{
            if(method.getName().equals("register") && args[0] instanceof LiteralCommandNode<?> node){
                dispatcher.getRoot().addChild((LiteralCommandNode<CommandSourceStack>)node);return Set.of(node.getName());
            }
            throw new UnsupportedOperationException(method.getName());
        });
        PaperCommands.register(registrar);return dispatcher;
    }
    @Test void typedRegistrationHasAllNamedCommandsAndCompletesThem()throws Exception{
        var dispatcher=registered();var feedback=new ArrayList<String>();var source=source(sender(true,Set.of(),feedback));
        assertEquals(Set.of("farlands","spokelands","greatsea","amplified","underworld","mist","unstableterrain"),dispatcher.getRoot().getChildren().stream().map(n->n.getName()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(1,dispatcher.execute("farlands list",source));assertEquals(1,dispatcher.execute("greatsea list",source));assertEquals(1,dispatcher.execute("spokelands list",source));
        assertTrue(feedback.stream().anyMatch(message->message.contains("skygrid")));
        var completions=dispatcher.getCompletionSuggestions(dispatcher.parse("farlands ",source)).get().getList().stream().map(s->s.getText()).toList();
        assertTrue(completions.containsAll(Arrays.asList(PaperCommands.FARLANDS_NAMES)));
        assertTrue(dispatcher.parse("farlands 1",source).getReader().canRead());
        for(String name:PaperCommands.FARLANDS_NAMES){
            var parsed=dispatcher.parse("farlands "+name,source);assertFalse(parsed.getReader().canRead());assertNotNull(parsed.getContext().getCommand());
        }
        var spokeCompletions=dispatcher.getCompletionSuggestions(dispatcher.parse("spokelands ",source)).get().getList().stream().map(s->s.getText()).toList();
        assertTrue(dispatcher.parse("spokelands 1",source).getReader().canRead());
        for(var region:SpokelandsTerrain.Region.values()){
            String name=SpokelandsCommand.name(region);assertTrue(spokeCompletions.contains(name));
            for(String suffix:new String[]{""," north"," west"}){
                var parsed=dispatcher.parse("spokelands "+name+suffix,source);assertFalse(parsed.getReader().canRead());assertNotNull(parsed.getContext().getCommand());
            }
            assertTrue(dispatcher.parse("spokelands "+name+" south",source).getReader().canRead());
        }
        assertNotNull(dispatcher.parse("spokelands",source).getContext().getCommand());
        var directions=dispatcher.getCompletionSuggestions(dispatcher.parse("spokelands plains ",source)).get().getList().stream().map(s->s.getText()).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("north","west"),directions);
    }
    @Test void everyCommandRequiresOperatorEvenWithLegacyPermissionGrants(){
        var dispatcher=registered();var feedback=new ArrayList<String>();
        var ordinary=source(sender(false,Set.of(),feedback));var operator=source(sender(true,Set.of(),feedback));
        var grants=new java.util.HashSet<String>();
        for(var node:dispatcher.getRoot().getChildren())grants.add("unstableterrain.command."+node.getName());
        grants.add("unstableterrain.command.settings");
        var granted=source(sender(false,grants,feedback));
        for(var node:dispatcher.getRoot().getChildren()){
            assertFalse(node.canUse(ordinary),node.getName());
            assertFalse(node.canUse(granted),node.getName());
            assertTrue(node.canUse(operator),node.getName());
        }
        for(String command:new String[]{"unstableterrain","unstableterrain settings","unstableterrain settings mobSpawning",
                "unstableterrain settings mobSpawning true","unstableterrain orbitalpull"})
            assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,()->dispatcher.execute(command,granted));
    }
    @Test void consoleTeleportReachesPlayerValidationInsteadOfUnknownCommand()throws Exception{
        var dispatcher=registered();var source=source(sender(true,Set.of(),new ArrayList<>()));
        var commands=new ArrayList<String>(java.util.List.of("greatsea","greatsea ocean","amplified","underworld","farlands floating","spokelands"));
        for(var region:SpokelandsTerrain.Region.values())for(String suffix:new String[]{""," north"," west"})commands.add("spokelands "+SpokelandsCommand.name(region)+suffix);
        for(String command:commands){
            var failure=assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,()->dispatcher.execute(command,source));
            assertEquals("Only a player can use this teleport command.",failure.getRawMessage().getString());
        }
    }
}
