function changeMaster(){
    var button_info = this.id.split('-');
    var user_id = button_info[0];
    var enable = button_info[1] === 'enable';
    var request = jsRoutes.controllers.HomeController.changeMaster(user_id, enable).ajax({
        type: 'POST'
    });
    request.done(function (response, textStatus, jqXHR){
        if(!enable){
            $('#' + user_id + '-disable').replaceWith('<button id="' + user_id +'-enable">Enable master</button>');
        } else {
            $('#' + user_id + '-enable').replaceWith('<button id="' + user_id +'-disable">Disable master</button>');
        }
        $('button').on('click', changeMaster);
    });
    request.fail(function (jqXHR, textStatus, errorThrown) {
        console.error("The following error occured: "+textStatus, errorThrown);
    });
}

$('button').on('click', changeMaster);

