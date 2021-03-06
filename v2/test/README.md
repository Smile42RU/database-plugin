

# beta release

|`Please note that this version is for testing purpose. **It must not be used on a production database**`|  |
|------------------------------------------------|--|
|                                                |  |

The beta5 release is now nearly fully functional :) and is compatible with both Archi 4.2 and future Archi 4.3.

Please do not forget to fill in an issue if you discover a misbehaviour.

## Known bugs:
These bugs need to be solved before the release of version 2.1:
* The sync process cannot (yet) be rolled back. So in case of an error in the middle of the export process, the model can be left in an inconsistent state
* When a component is moved to another folder, it is not seen as a component change. If only folder moves are done, then the export plugin may say that the model is up to date even if it is not.
* In the "import individual component", the "ignore case" button seems to malfunction, importing embedded views does not recursively imports the linked view and the images may not be imported as expected
* The plugins requires a lot of memory, often leading to increase the JVM size (set java parameter -Xmx1024 in Archi.ini file)

## Planned improvements
In addition, some improvements are planned but it's not guaranteed that they will be part of the version 2.1:
* Improve the inline help and the GitHub wiki that lack a lot of information. Specifically, the algorithms should be described.
* Improve import's performance by gathering all the objects properties and connections boundpoints in hashmaps in order to reduce the number of SQL requests generated
* Add a "do not share" property that will force the "import individual component" to create a copy of the components. This way, it will be possible to implement, in the same view, some components that will be shared and other duplicated across models. The aim is to allow the creation of templates.
* Develop a database admin interface with housekeeping (delete a model, delete a specific model's version, list and delete components that are not part of any model, vacuum database, backup database, restore database, creation of indexes for big databases, ...)
* Allow to replace a component by another one (and moving all the relations to this new component)
* Allow to change a component's class (verifying the relationships conformity)
* Now that the sync works, allow the creation of branches rather than a linear version management
* Add support for new database brands (DB2, Sybase, MongoDB, and more generally ODBC)
* Allow to update a model from the database without exporting our own changes
* Create a window that provide detailed statistics about the model and its content
* Add an option to duplicate a model
* Add an option to merge models
* For the "import individual component":
  * Allow to recursively import elements that have got relations with the imported element
  * Allow to select all the classes of one group in one click
  * Add some explanation on the window that is not really intuitive
  * Add an option to import relationships to/from a component that would be in the database but not in the model
* for the "component history":
  * Allow to export individual component to the database to share a small update with colleagues
  * Allow to update a component from the database without exporting our own changes to get colleagues changes
  * Allow to get history for folders and views

## Changes from the version 2.0.7b:
* *Import Model*
  * an import model from database context menu entry has been added when no model is selected
* *Import individual component:*
  * Added documentation column in the array to help distinguish components having the same name
  * Add popup message during the import as it may take some time
* *Export model:*
  * Complete rewrite of the comparison management (use timestamps in addition of version number as it is possible to switch from a database to another)
  * In case of exception, the database lock is released before the error message is displayed
  * An option has been added to show / not show the model's comparison to the database before the export (showing gives more information to the user but it takes some time on big models)
  * For **relational databases**:
    * Create two export modes: standalone and collaborative modes:
      * **Standalone mode** means the export procedure only export components (it is quicker and adapted when only one person work on a model at one time)
      * **Collaborative mode** means the export procedure syncs the model with the database (it is slower but adapted when several people are working on the same model at the same time)
    * Allow to specify border width and scale factor for views screenshots
  * For **Neo4J databases**:
    * Create two export modes: native and extended:
      * **Native mode** means that Archi relationships are exported as Neo4J relationships (but this mode does not allow to export relationships on relationships)
      * **Extended mode** means that Archi relationships are exported as Neo4J nodes (this mode makes Neo4J diagrams more complex but allow relationships on relationships)
    * New option to empty the database before the export
    * New option to specialize relationships
* *Get history from database:*
  * Allow to get history for diagrams, canvas and sketches
* *Other:*
  * bug fixes:
    * Check for null images before export (this shouldn't happen but it does happen)
    * Fix plugin initialization failure that occured some times
    * Fix progress bar during download new version of the plugin from GitHub
    * Increase compiler severity to maximum and resolve all the warnings to improve code resiliency
    * Reduce memory leak
    * Fix centering of GUI windows, especially on hiDPI displays
    * Fix calculation of numbers of images to import
    * Better handling of the cancel button
  * Improvements:
    * Add the ability to import an image from the database (on the Image and Block objects in Canvas)
    * Remove the name, the documentation and the properties from view objects and connections checksum as they are not related to the view objects and connections themselves, but to the related element or relationship
    * Add procedures that can be called by the script plugin
    * The online help is finally beeing written. It can be accessed using the interrogation mark on every plugin window.
    * Export and import back the model's "metadata" (may be used by other external tools)
    * Do not calculate checksum on images anymore
    * Update JDBC drivers
      * Neo4J to 3.1.0
      * SQLite to 3.21.0
      * PostGreSQL to 42.2.1