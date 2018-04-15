/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
//test function to attempt image retrieval from the database

//function show() {  //originally buttons were going to disappear when you clicked them. no longer something that happens
//    if (document.layers)
//        document.layers['mydiv'].visibility = "show";
//    if (document.getElementById)
//        document.getElementById("mydiv").style.visibility = "visible";
//    if (document.all)
//        document.all.mydiv.style.visibility = "visible";
//}
//
//function hide() {
//    if (document.layers)
//        document.layers['mydiv'].visibility = "hide";
//    if (document.getElementById)
//        document.getElementById("mydiv").style.visibility = "hidden";
//    if (document.all)
//        document.all.mydiv.style.visibility = "hidden";
//}

function request(obj) {
    return new Promise((resolve, reject) => {
        let xhr = new XMLHttpRequest();
        //if no alternative method is supplied default to a get route
        xhr.open(obj.method || "GET", obj.url);
        xhr.onload = () => {
            if (xhr.status >= 200 && xhr.status < 300) {
                resolve(xhr.response);
            } else {
                reject(xhr.statusText);
            }
        };
        xhr.onerror = () => reject(xhr.statusText);

        xhr.send(obj.body);
    });
}

function getImageData(index) {
    request({url: "/getImageData?index=" + index})
            .then(data => {
                //unpack and compile meta data into a string
                var data = JSON.parse(data);
                var metaData = "Beam Intensity (kv): " + data[1].toString() + "<br>" + "Magnification(um): " + data[2].toString() + "<br>" + "Working Depth (mm) " + data[3].toString() + "<br>" + "Up Votes: " + data[4].toString() + "<br>" + "Operators: " + data[5].toString();
                var metaDataParagraph = document.getElementById(index);
                metaDataParagraph.innerHTML = metaData;
            })
            .catch(error => {
                console.log(error);
            });

}

function combineImageDivElements(metaDataParagraph, elements) {
    //unpack elements:
    var responsiveDiv = elements[0];
    var galleryDiv = elements[1];
    var image = elements[2];
    var upvote = elements[3];
    var downvote = elements[4];

    //put elements together
    responsiveDiv.appendChild(galleryDiv);
    galleryDiv.appendChild(image);
    galleryDiv.appendChild(metaDataParagraph);
    galleryDiv.appendChild(upvote);
    galleryDiv.appendChild(downvote);

    return responsiveDiv;
    //document.body.append(responsiveDiv);
}

function buildImageDivWrappers(index) {
    var responsiveDiv = document.createElement("div");
    responsiveDiv.setAttribute("class", "responsive");
    //build image element
    var image = document.createElement("img");
    image.setAttribute("src", "/getImage?index=" + index);
    image.setAttribute("width", "300");
    image.setAttribute("height", "200");

    var galleryDiv = document.createElement("div");
    galleryDiv.setAttribute("class", "gallery");

    //build upvote and downvote buttons
    var upvote = document.createElement("button");
    upvote.setAttribute("onclick", "updateVote(" + index + ",true)");
    upvote.innerHTML = "Upvote";
    upvote.style.color = "green";
    
    
    var downvote = document.createElement("button");
    downvote.setAttribute("onclick", "updateVote(" + index + ",false)");
    downvote.innerHTML = "Downvote";
    downvote.style.color = "red";


    var metaDataParagraph = document.createElement("p");
    //since every image has a unique index this allows for the association of meta data with the image
    //trouble is if we build the paragraph inside the .then statement of the request for the meta data
    //it returns undefined prematurely
    //ergo the solution is to make it here and then use the .then to edit the inner html
    metaDataParagraph.setAttribute("id", index);
    //this will edit the paragraph to add in the metadata
    getImageData(index);

    //put elements together
    responsiveDiv.appendChild(galleryDiv);
    galleryDiv.appendChild(image);
    galleryDiv.appendChild(metaDataParagraph);
    galleryDiv.appendChild(upvote);
    galleryDiv.appendChild(downvote);

    return responsiveDiv;
}

function updateVote(index, increment) {
    request({url: "/updateVote?index=" + index + "&increment=" + increment})
            .then(data => {
                var data = JSON.parse(data);
//                var temp = request({url: "/getCookie?var=" + data});
//                if(temp == "false") {                                              //cookie implementation; doesn't work right now (even though it is beautifully written)
//                    request({url: "/updateVote?index=" + index + "&increment="+ increment});
//                }
//                else{
                //request({url: "/setCookie?data=" + data});
                //    }
                var metaData = "Beam Intensity (kv): " + data[1].toString() + "<br>" + "Magnification(um): " + data[2].toString() + "<br>" + "Working Depth (mm) " + data[3].toString() + "<br>" + "Up Votes: " + data[4].toString() + "<br>" + "Operators: " + data[5].toString();
                var metaDataParagraph = document.getElementById(index);
                metaDataParagraph.innerHTML = metaData;
            })
            .catch(error => {
                console.log(error);
            });
}

function getImagesByPopularity() {
    request({url: "/getImagesByPopularity"})
            .then(data => {
                //clear existing images;
                document.getElementById("images").innerHTML = "";
                var indexes = JSON.parse(data);
                //in order to enforce the deployment order of every image into the html, we set the order ahead of time
                //this gets done by appending the divs as we make them rather than appending the divs at the end of the div maker function
                for (var i = 0; i < indexes.length; i++) {
                    //add proper div into the html
                    document.getElementById("images").appendChild(buildImageDivWrappers(indexes[i]));
                }
            })
            .catch(error => {
                console.log(error);
            });
}

function getImagesByColored() {
    request({url: "/getImagesByColored"})
            .then(data => {
                //clear existing images;
                document.getElementById("images").innerHTML = "";
                var indexes = JSON.parse(data);
                //in order to enforce the deployment order of every image into the html, we set the order ahead of time
                //this gets done by appending the divs as we make them rather than appending the divs at the end of the div maker function
                for (var i = 0; i < indexes.length; i++) {
                    //add proper div into the html
                    document.getElementById("images").appendChild(buildImageDivWrappers(indexes[i]));
                }
            })
            .catch(error => {
                console.log(error);
            });
}

function getImagesByTimestamp() {
    request({url: "/getImagesByTimestamp"})
            .then(data => {
                //clear existing images;
                document.getElementById("images").innerHTML = "";
                var indexes = JSON.parse(data);
                //in order to enforce the deployment order of every image into the html, we set the order ahead of time
                //this gets done by appending the divs as we make them rather than appending the divs at the end of the div maker function
                for (var i = 0; i < indexes.length; i++) {
                    //add proper div into the html
                    document.getElementById("images").appendChild(buildImageDivWrappers(indexes[i]));
                }
            })
            .catch(error => {
                console.log(error);
            });
}