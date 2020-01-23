function cleanURLToID(url) {
    return url.replace(/[:\/?.]/g, '');
}

function validateContents() {
    var rootElement = document.querySelector(`.moodleRoot`);
    var formData = new FormData(rootElement);

    var courseID = formData.get("course");
    var modules = formData.getAll("module");
    var files = formData.getAll("file");
    var parents = formData.getAll("parent");

    var cleanFiles = files.map(cleanURLToID);

    if (courseID != null) {
        // if the course is selected every file will at least go to it
        return true;
    }

    var fileParentsMap = {};
    var cleanID;
    var fileParentPresentMap = {};

    for (var i in parents) {
        var parts = parents[i].split("$$$");
        cleanID = cleanURLToID(parts[0]);
        fileParentsMap[cleanID] = parts[1].split(",");
        if(cleanFiles.indexOf(cleanID)!==-1){
            fileParentPresentMap[cleanID] = false;
        }
    }

    for(var j in files) {
        var fileURL = files[j];
        cleanID = cleanURLToID(fileURL);
        var parentIDs = fileParentsMap[cleanID];
        for(var l in parentIDs){
            var parentID = parentIDs[l];
            if(modules.indexOf(parentID)!==-1){
                fileParentPresentMap[cleanID] = true;
                break;
            }
        }
    }

    const validationMessage = document.querySelector(".fileNotLinkedValidation");
    var allTrue= true;
    var hideClass = "d-none";
    for(var fileUrl in fileParentPresentMap){
        if(!fileParentPresentMap[fileUrl]){
            if(validationMessage.classList.contains(hideClass)) {
                validationMessage.classList.remove(hideClass);
            }
            allTrue=false;
            break;
        }
    }
    if(allTrue){
        if(!validationMessage.classList.contains(hideClass)){
            validationMessage.classList.add(hideClass);
        }
        return true;
    }

    return false;
}