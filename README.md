# Scala challenge

Hello future colleague! In this assessment, we request that you implement an HTTP application that converts CSV data sets to JSON data sets. The application will provide the following endpoints:
 
## Create Task 
#### POST /task/
Create a task with URI pointing to CSV dataset which will be converted to Json and reuturns taskId.
   - 2 tasks can be run at the same time
   - the task is executed immediately if `running-tasks < 2` 

## List Tasks 
#### GET /task/

## Task Detail 
#### GET /task/<taskId>
Return informations about the task:    
- lines processed
- avg lines processed (count/sec)
- state (`SCHEDULED/RUNNING/DONE/FAILED/CANCELED`)
- result (uri where the JSON file can be downloaded)

Keep the connection open until the task isn't in a terminal state and send the updated response every 2 seconds.

## Cancel Task
#### DELETE /task/<taskId>
Tasks in `SCHEDULED` or `RUNNING` state can be canceled.

## Get JSON File
Endpoint providing generated JSON files.

## Notes
- Keep the state only in memory except generated json files.
- Take into account that input files don't have to fit into memory.

CSV datasets for testing purposes can be found at the following links:
* https://catalog.data.gov/dataset?res_format=CSV
* https://www.kaggle.com/datasets

Feel free to ask any questions through email or github.

Good Luck!
