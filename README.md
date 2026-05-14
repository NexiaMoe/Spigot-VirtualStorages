### VirtualStorages Plugin

VirtualStorages is a Minecraft Plugin designed to enhance storage capabilities for players on servers. It introduces virtual backpacks, allowing players to store items in a virtual compartment that is accessible from anywhere in the game.

[<img width="187" height="64" alt="modrinth_64h" src="https://github.com/user-attachments/assets/893d4e6e-3a82-4746-993f-f4281281932a" />](https://modrinth.com/plugin/virtualstorages-backpacks)


---

**Key Features:**

- **Virtual Backpacks:**  
  - Players can access additional storage slots inside a virtual backpack.
  - Backpacks are accessible from anywhere in the game.

- **Customizable:**  
  - Allow via permissions to set the numbers a player can have or who can use virtual backpacks.
  - Modify titles and other aspects of the backpacks.

- **Admin Control:**  
  - Admins can inspect and manipulate the contents of any player's backpack using `/backpackview {player}`.
  - Backpack data is stored in files located in the plugin's Data Folder.

---

**Installation:**

Requires Spigot 26.1.x and Java 25 or newer for version 1.5.0.0.

1. **Download:**  
   - Get the latest version of VirtualStorages from the [releases page](https://github.com/852DuartePls/UNSTABLE-Spigot-VirtualStorages/releases).
   
2. **Place Plugin:**  
   - Put the plugin JAR file in your server's `plugins` folder.
   
3. **Restart Server:**  
   - Start or restart your Minecraft server to load the plugin.

---

**Commands:**

- **/backpack:** (<ins>alias: `/bp`</ins>) 
  - Opens the virtual backpack for players with the appropriate permission.

- **/backpackview {player}** (<ins>alias: `/bpview {player}`</ins>)  
  - Allows admins to view and interact with another player's backpack.

- **/vsreload:**  
  - Reloads messages from the `config.yml` file. Requires admin permission.

---

**Permissions:**

- **virtualstorages.use.#:**  
  - Allows access to virtual backpacks, where "#" is the backpack number allowed for the player to have (1-999).

- **virtualstorages.admin:**  
  - Grants access to `/vsreload` and `/backpackview` commands.

> [!IMPORTANT] 
> A permissions plugin is required to manage these permissions effectively.

---

**Feedback:**  
Your feedback is valuable! Feel free to submit bug reports, feature requests, or general feedback on the [Issues section](https://github.com/852DuartePls/UNSTABLE-Spigot-VirtualStorages/issues).

---

#### **TODO:**
- [x] Add a way to see other players' backpacks in-game
- [x] Make `/vsreload` no longer necessary for updating allowed pages for players
- [ ] Add option for Database storage
