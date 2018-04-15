/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

(function(){
    var vid =document.querySelector('video');
    
    var constraints={
        video:true,
        audio:false
    };
    navigator.mediaDevices.getUserMedia(constraints).then(function(stream){
        vid.srcObject=stream;
        vid.play();
    }).catch(function(err){

    });
})();
