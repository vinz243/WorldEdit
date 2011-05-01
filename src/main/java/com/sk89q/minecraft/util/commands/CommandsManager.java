// $Id$
/*
 * WorldEdit
 * Copyright (C) 2010 sk89q <http://www.sk89q.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.sk89q.minecraft.util.commands;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.sk89q.util.StringUtil;

/**
 * Manager for handling commands. This allows you to easily process commands,
 * including nested commands, by correctly annotating methods of a class.
 * The commands are thus declaratively defined, and it's easy to spot
 * how permissions and commands work out, and it decreases the opportunity
 * for errors because the consistency would cause any odd balls to show. 
 * The manager also handles some boilerplate code such as number of arguments
 * checking and printing usage.
 * 
 * <p>To use this, it is merely a matter of registering classes containing
 * the commands (as methods with the proper annotations) with the
 * manager. When you want to process a command, use one of the
 * <code>execute</code> methods. If something is wrong, such as incorrect
 * usage, insufficient permissions, or a missing command altogether, an
 * exception will be raised for upstream handling.
 * 
 * <p>To mark a method as a command, use {@link Command}. For nested commands,
 * see {@link NestedCommand}. To handle permissions, use
 * {@link CommandPermissions}.
 * 
 * <p>This uses Java reflection extensively, but to reduce the overhead of
 * reflection, command lookups are completely cached on registration. This
 * allows for fast command handling. Method invocation still has to be done
 * with reflection, but this is quite fast in that of itself.
 * 
 * @author sk89q
 * @param <T> command sender class
 */
public abstract class CommandsManager<T> {
    
    /**
     * Mapping of commands (including aliases) with a description. Root
     * commands are stored under a key of null, whereas child commands are
     * cached under their respective {@link Method}.
     */
    protected Map<Method, Map<String, Method>> commands
            = new HashMap<Method, Map<String, Method>>();
    
    /**
     * Mapping of commands (not including aliases) with a description.
     */
    protected Map<String, String> descs = new HashMap<String, String>();
    
    /**
     * Register an object that contains commands (denoted by
     * {@link Command}. The methods are
     * cached into a map for later usage and it reduces the overhead of
     * reflection (method lookup via reflection is relatively slow).
     * 
     * @param cls
     */
    public void register(Class<?> cls) {
        registerMethods(cls, null);
    }
    
    /**
     * Register the methods of a class.
     * 
     * @param cls
     * @param parent
     */
    private void registerMethods(Class<?> cls, Method parent) {
        Map<String, Method> map;
        
        // Make a new hash map to cache the commands for this class
        // as looking up methods via reflection is fairly slow
        if (commands.containsKey(parent)) {
            map = commands.get(parent);
        } else {
            map = new HashMap<String, Method>();
            commands.put(parent, map);
        }
        
        for (Method method : cls.getMethods()) {
            if (!method.isAnnotationPresent(Command.class)) {
                continue;
            }

            Command cmd = method.getAnnotation(Command.class);
            
            // Cache the aliases too
            for (String alias : cmd.aliases()) {
                map.put(alias, method);
            }
            
            // Build a list of commands and their usage details, at least for
            // root level commands
            if (parent == null) {
                if (cmd.usage().length() == 0) {
                    descs.put(cmd.aliases()[0], cmd.desc());
                } else {
                    descs.put(cmd.aliases()[0], cmd.usage() + " - " + cmd.desc());
                }
            }
            
            // Look for nested commands -- if there are any, those have
            // to be cached too so that they can be quickly looked
            // up when processing commands
            if (method.isAnnotationPresent(NestedCommand.class)) {
                NestedCommand nestedCmd = method.getAnnotation(NestedCommand.class);
                
                for (Class<?> nestedCls : nestedCmd.value()) {
                    registerMethods(nestedCls, method);
                }
            }
        }
    }
    
    /**
     * Checks to see whether there is a command named such at the root level.
     * This will check aliases as well.
     * 
     * @param command
     * @return
     */
    public boolean hasCommand(String command) {
        return commands.get(null).containsKey(command.toLowerCase());
    }
    
    /**
     * Get a list of command descriptions. This is only for root commands.
     * 
     * @return
     */
    public Map<String, String> getCommands() {
        return descs;
    }
    
    /**
     * Get the usage string for a command.
     * 
     * @param args
     * @param level
     * @param cmd
     * @return
     */
    protected String getUsage(String[] args, int level, Command cmd) {
        StringBuilder command = new StringBuilder();
        
        command.append("/");
        
        for (int i = 0; i <= level; i++) {
            command.append(args[i] + " ");
        }
        
        command.append(cmd.flags().length() > 0 ? "[-" + cmd.flags() + "] " : "");
        command.append(cmd.usage());
        
        return command.toString();
    }
    
    /**
     * Get the usage string for a nested command.
     * 
     * @param args
     * @param level
     * @param method
     * @param player
     * @return
     * @throws CommandException
     */
    protected String getNestedUsage(String[] args, int level,
            Method method, T player) throws CommandException {
        
        StringBuilder command = new StringBuilder();
        
        command.append("/");
        
        for (int i = 0; i <= level; i++) {
            command.append(args[i] + " ");
        }

        
        Map<String, Method> map = commands.get(method);
        boolean found = false;
        
        command.append("<");
        
        Set<String> allowedCommands = new HashSet<String>();
        
        for (Map.Entry<String, Method> entry : map.entrySet()) {
            Method childMethod = entry.getValue();
            found = true;
            
            if (hasPermission(childMethod, player)) {
                Command childCmd = childMethod.getAnnotation(Command.class);
                
                allowedCommands.add(childCmd.aliases()[0]);
            }
        }
        
        if (allowedCommands.size() > 0) {
            command.append(StringUtil.joinString(allowedCommands, "|", 0));
        } else {
            if (!found) {
                command.append("?");
            } else {
                //command.append("action");
                throw new CommandPermissionsException();
            }
        }
        
        command.append(">");
        
        return command.toString();
    }
    
    /**
     * Attempt to execute a command. This version takes a separate command
     * name (for the root command) and then a list of following arguments.
     * 
     * @param cmd command to run
     * @param args arguments
     * @param player command source
     * @param methodArgs method arguments
     * @throws CommandException 
     */
    public void execute(String cmd, String[] args, T player,
            Object ... methodArgs) throws CommandException {
        
        String[] newArgs = new String[args.length + 1];
        System.arraycopy(args, 0, newArgs, 1, args.length);
        newArgs[0] = cmd;
        Object[] newMethodArgs = new Object[methodArgs.length + 1];
        System.arraycopy(methodArgs, 0, newMethodArgs, 1, methodArgs.length);
        
        executeMethod(null, newArgs, player, newMethodArgs, 0);
    }
    
    /**
     * Attempt to execute a command.
     * 
     * @param args
     * @param player
     * @param methodArgs
     * @throws CommandException 
     */
    public void execute(String[] args, T player,
            Object ... methodArgs) throws CommandException {
        
        Object[] newMethodArgs = new Object[methodArgs.length + 1];
        System.arraycopy(methodArgs, 0, newMethodArgs, 1, methodArgs.length);
        executeMethod(null, args, player, newMethodArgs, 0);
    }
    
    /**
     * Attempt to execute a command.
     * 
     * @param parent
     * @param args
     * @param player
     * @param methodArgs
     * @param level
     * @throws CommandException 
     */
    public void executeMethod(Method parent, String[] args,
            T player, Object[] methodArgs, int level) throws CommandException {
        
        String cmdName = args[level];
        
        Map<String, Method> map = commands.get(parent);
        Method method = map.get(cmdName.toLowerCase());
        
        if (method == null) {
            if (parent == null) { // Root
                throw new UnhandledCommandException();
            } else {
                throw new MissingNestedCommandException("Unknown command: " + cmdName,
                        getNestedUsage(args, level - 1, parent, player));
            }
        }
        
        if (!hasPermission(method, player)) {
            throw new CommandPermissionsException();
        }
        
        int argsCount = args.length - 1 - level;

        if (method.isAnnotationPresent(NestedCommand.class)) {
            if (argsCount == 0) {
                throw new MissingNestedCommandException("Sub-command required.", 
                        getNestedUsage(args, level, method, player));
            } else {
                executeMethod(method, args, player, methodArgs, level + 1);
            }
        } else {
            Command cmd = method.getAnnotation(Command.class);
            
            String[] newArgs = new String[args.length - level];
            System.arraycopy(args, level, newArgs, 0, args.length - level);
            
            CommandContext context = new CommandContext(newArgs);
            
            if (context.argsLength() < cmd.min()) {
                throw new CommandUsageException("Too few arguments.",
                        getUsage(args, level, cmd));
            }
            
            if (cmd.max() != -1 && context.argsLength() > cmd.max()) {
                throw new CommandUsageException("Too many arguments.",
                        getUsage(args, level, cmd));
            }
            
            for (char flag : context.getFlags()) {
                if (cmd.flags().indexOf(String.valueOf(flag)) == -1) {
                    throw new CommandUsageException("Unknown flag: " + flag,
                            getUsage(args, level, cmd));
                }
            }
            
            methodArgs[0] = context;
            
            try {
                method.invoke(null, methodArgs);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof CommandException) {
                    throw (CommandException) e.getCause();
                }
                
                throw new WrappedCommandException(e.getCause());
            }
        }
    }
    
    /**
     * Returns whether a player has access to a command.
     * 
     * @param method
     * @param player
     * @return
     */
    protected boolean hasPermission(Method method, T player) {
        CommandPermissions perms = method.getAnnotation(CommandPermissions.class);
        if (perms == null) {
            return true;
        }
        
        for (String perm : perms.value()) {
            if (hasPermission(player, perm)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Returns whether a player permission..
     * 
     * @param player
     * @param perm
     * @return
     */
    public abstract boolean hasPermission(T player, String perm);
}